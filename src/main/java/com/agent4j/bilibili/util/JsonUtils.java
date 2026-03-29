package com.agent4j.bilibili.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String write(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    public static JsonNode readTree(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    public static String extractJsonBlock(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("LLM returned empty content");
        }
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int start = -1;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '{' || current == '[') {
                start = index;
                break;
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("LLM response does not contain valid JSON");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{' || current == '[') {
                depth++;
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth == 0) {
                    return value.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("LLM response does not contain valid JSON");
    }

    public static String text(JsonNode node, String fieldName) {
        if (node == null || node.get(fieldName) == null || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText("");
    }

    public static boolean has(JsonNode node, String fieldName) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull();
    }

    public static boolean isObject(JsonNode node) {
        return node != null && node.isObject();
    }

    public static Iterable<JsonNode> elements(JsonNode node) {
        return node == null ? java.util.List.<JsonNode>of() : node::elements;
    }

    public static Iterable<String> fieldNames(JsonNode node) {
        return node == null ? java.util.List.<String>of() : node::fieldNames;
    }
}
