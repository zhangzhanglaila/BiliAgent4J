package com.agent4j.bilibili.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;

public final class JsonUtils {

    /**
     * 工具类，禁止实例化。
     */
    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param objectMapper Jackson 对象映射器
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    public static String write(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    /**
     * 将 JSON 字符串解析为节点树。
     *
     * @param objectMapper Jackson 对象映射器
     * @param value JSON 字符串
     * @return 解析后的 JSON 节点
     */
    public static JsonNode readTree(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    /**
     * 从模型返回文本中提取完整 JSON 片段。
     *
     * @param raw 原始响应文本
     * @return 提取出的 JSON 内容
     */
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

    /**
     * 读取节点中的字符串字段。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 字段文本，不存在时返回空串
     */
    public static String text(JsonNode node, String fieldName) {
        if (node == null || node.get(fieldName) == null || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText("");
    }

    /**
     * 判断节点是否包含非空字段。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 是否存在且不为 null
     */
    public static boolean has(JsonNode node, String fieldName) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull();
    }

    /**
     * 判断节点是否为对象类型。
     *
     * @param node JSON 节点
     * @return 是否为对象节点
     */
    public static boolean isObject(JsonNode node) {
        return node != null && node.isObject();
    }

    /**
     * 返回节点的子元素迭代结果。
     *
     * @param node JSON 节点
     * @return 子元素集合，不存在时返回空集合
     */
    public static Iterable<JsonNode> elements(JsonNode node) {
        return node == null ? java.util.List.<JsonNode>of() : node::elements;
    }

    /**
     * 返回节点的字段名迭代结果。
     *
     * @param node JSON 节点
     * @return 字段名集合，不存在时返回空集合
     */
    public static Iterable<String> fieldNames(JsonNode node) {
        return node == null ? java.util.List.<String>of() : node::fieldNames;
    }
}
