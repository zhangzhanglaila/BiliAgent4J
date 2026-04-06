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

    /**
     * 创建代码解释器服务。
     *
     * @param objectMapper JSON 映射器
     */
    public CodeInterpreterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 使用 JShell 执行 Java 代码并返回执行结果。
     *
     * @param payload 包含 code 和 variables 的载荷
     * @return 执行结果，包含 stdout、result 和 error 字段
     */
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

    /**
     * 构建 JShell 预置代码，导入常用包并注入变量。
     *
     * @param variables 外部变量映射
     * @return 预置代码行列表
     */
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

    /**
     * 在 JShell 中执行代码并检查结果。
     *
     * @param shell JShell 实例
     * @param code 要执行的代码
     */
    private void evaluate(JShell shell, String code) {
        List<SnippetEvent> events = shell.eval(code);
        for (SnippetEvent event : events) {
            if (event.exception() != null || event.status() == Snippet.Status.REJECTED) {
                throw new IllegalStateException("Failed to initialize JShell context");
            }
        }
    }

    /**
     * 将原始 Map 转换为字符串键 Map。
     *
     * @param source 原始映射
     * @return 字符串键映射
     */
    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * 安全转换为字符串并去除首尾空白。
     *
     * @param value 原始值
     * @return 字符串结果
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
