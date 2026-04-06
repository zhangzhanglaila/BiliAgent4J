package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SearchToolService {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 创建搜索工具服务。
     *
     * @param properties 系统配置
     * @param objectMapper JSON 映射器
     */
    public SearchToolService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    /**
     * 使用 SerpAPI 搜索 Google 并返回结果。
     *
     * @param query 搜索关键词
     * @param limit 返回结果数量上限
     * @return 搜索结果
     */
    public Map<String, Object> search(String query, int limit) {
        String cleanQuery = stringValue(query);
        if (cleanQuery.isBlank()) {
            return Map.of("query", cleanQuery, "results", List.of(), "warning", "empty_query");
        }
        String apiKey = stringValue(properties.getSerpapiApiKey());
        if (apiKey.isBlank()) {
            return Map.of("query", cleanQuery, "results", List.of(), "warning", "missing_serpapi_api_key");
        }

        int size = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 10));
        String url = "https://serpapi.com/search.json?engine=google"
                + "&q=" + encode(cleanQuery)
                + "&api_key=" + encode(apiKey)
                + "&num=" + size
                + "&hl=zh-cn&gl=cn";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            String body = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            JsonNode payload = objectMapper.readTree(body);
            List<Map<String, String>> results = new ArrayList<>();
            for (JsonNode item : payload.path("organic_results")) {
                if (results.size() >= size) {
                    break;
                }
                Map<String, String> result = new LinkedHashMap<>();
                result.put("title", item.path("title").asText(""));
                result.put("link", item.path("link").asText(""));
                result.put("snippet", item.path("snippet").asText(""));
                results.add(result);
            }
            return Map.of("query", cleanQuery, "results", results);
        } catch (Exception exception) {
            return Map.of("query", cleanQuery, "results", List.of(), "error", exception.getMessage());
        }
    }

    /**
     * 对字符串进行 URL 编码。
     *
     * @param value 原始字符串
     * @return 编码后的字符串
     */
    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
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
