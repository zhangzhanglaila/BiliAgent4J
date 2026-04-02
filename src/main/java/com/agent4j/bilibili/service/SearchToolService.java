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

    public SearchToolService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

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

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
