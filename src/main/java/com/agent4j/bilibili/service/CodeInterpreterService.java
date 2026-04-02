package com.agent4j.bilibili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import org.springframework.stereotype.Service;

@Service
public class CodeInterpreterService {

    private final ObjectMapper objectMapper;

    public CodeInterpreterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> run(Map<String, Object> payload) {
        String code = stringValue(payload == null ? null : payload.get("code"));
        Map<String, Object> variables = payload != null && payload.get("variables") instanceof Map<?, ?> source
                ? castMap(source)
                : Map.of();
        if (code.isBlank()) {
            return Map.of("stdout", "", "result", "", "error", "missing_code");
        }

        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);
             JShell shell = JShell.builder().out(out).err(err).build()) {

            for (String prelude : buildPrelude(variables)) {
                evaluate(shell, prelude);
            }

            String lastValue = "";
            List<SnippetEvent> events = shell.eval(code);
            for (SnippetEvent event : events) {
                if (event.exception() != null) {
                    return Map.of(
                            "stdout", stdoutBuffer.toString(StandardCharsets.UTF_8),
                            "result", "",
                            "error", event.exception().getMessage()
                    );
                }
                if (event.status() == Snippet.Status.REJECTED) {
                    return Map.of(
                            "stdout", stdoutBuffer.toString(StandardCharsets.UTF_8),
                            "result", "",
                            "error", shell.diagnostics(event.snippet())
                                    .map(diagnostic -> diagnostic.getMessage(null))
                                    .findFirst()
                                    .orElse("snippet_rejected")
                    );
                }
                if (event.value() != null && !event.value().isBlank()) {
                    lastValue = event.value();
                }
            }

            return Map.of(
                    "stdout", stdoutBuffer.toString(StandardCharsets.UTF_8),
                    "result", lastValue,
                    "error", stderrBuffer.toString(StandardCharsets.UTF_8).trim()
            );
        } catch (Exception exception) {
            return Map.of("stdout", "", "result", "", "error", exception.getMessage());
        }
    }

    private List<String> buildPrelude(Map<String, Object> variables) {
        try {
            String json = objectMapper.writeValueAsString(variables == null ? Map.of() : variables)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            return List.of(
                    "import java.util.*;",
                    "import com.fasterxml.jackson.databind.*;",
                    "ObjectMapper __mapper = new ObjectMapper();",
                    "Map<String, Object> variables = __mapper.readValue(\"" + json + "\", LinkedHashMap.class);"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build interpreter prelude", exception);
        }
    }

    private void evaluate(JShell shell, String code) {
        List<SnippetEvent> events = shell.eval(code);
        for (SnippetEvent event : events) {
            if (event.exception() != null || event.status() == Snippet.Status.REJECTED) {
                throw new IllegalStateException("Failed to initialize JShell context");
            }
        }
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
