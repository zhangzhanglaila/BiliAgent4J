package com.agent4j.bilibili.service;

import com.agent4j.bilibili.util.JsonUtils;
import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class BilibiliHttpSupport {

    public static final Pattern BVID_PATTERN = Pattern.compile("(BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INITIAL_STATE_PATTERN = Pattern.compile(
            "(?:window\\.__INITIAL_STATE__=|__INITIAL_STATE__=)(\\{.*?\\})(?:\\s*;|</script>)",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BilibiliHttpSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    public String fetchText(String url, Duration timeout) {
        try {
            // Keep headers close to a normal browser request to reduce public endpoint variance.
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (Exception exception) {
            throw new IllegalStateException("请求失败: " + exception.getMessage(), exception);
        }
    }

    public JsonNode fetchJson(String url) {
        String text = fetchText(url, Duration.ofSeconds(10));
        JsonNode payload = JsonUtils.readTree(objectMapper, text);
        if (!payload.isObject()) {
            throw new IllegalArgumentException("B站接口返回了无效数据");
        }
        return payload;
    }

    public String resolveShortLink(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        List<String> hosts = List.of("b23.tv", "bili2233.cn");
        if (hosts.stream().noneMatch(url::contains)) {
            return url;
        }
        try {
            // Short links are resolved by following redirects and reading the final URI.
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.uri().toString();
        } catch (Exception exception) {
            return url;
        }
    }

    public JsonNode extractInitialState(String html) {
        Matcher matcher = INITIAL_STATE_PATTERN.matcher(html == null ? "" : html);
        if (!matcher.find()) {
            return objectMapper.createObjectNode();
        }
        try {
            // Bilibili pages often expose the full video payload through __INITIAL_STATE__.
            return objectMapper.readTree(matcher.group(1));
        } catch (IOException exception) {
            return objectMapper.createObjectNode();
        }
    }

    public String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String stripHtml(String value) {
        return Jsoup.parse(value == null ? "" : value).text().trim();
    }

    public String meta(Document document, String selector) {
        return document.select(selector).attr("content");
    }

    public String extractRegex(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public String value(JsonNode node, String fieldName, String fallback) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            String value = node.get(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    public Map<String, Object> mapOf(JsonNode node) {
        return objectMapper.convertValue(
                node == null ? objectMapper.createObjectNode() : node,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
        );
    }

    public int safeInt(JsonNode node) {
        return TextUtils.safeInt(node == null ? null : node.asText(""));
    }
}
