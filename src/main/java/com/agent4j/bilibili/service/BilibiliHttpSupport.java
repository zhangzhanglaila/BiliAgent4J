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

    /**
     * 创建 B 站 HTTP 支持组件。
     *
     * @param objectMapper JSON 映射器
     */
    public BilibiliHttpSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    /**
     * 请求指定地址并返回文本内容。
     *
     * @param url 请求地址
     * @param timeout 超时时间
     * @return 响应文本
     */
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

    /**
     * 请求指定地址并解析 JSON 数据。
     *
     * @param url 请求地址
     * @return 解析后的 JSON 节点
     */
    public JsonNode fetchJson(String url) {
        String text = fetchText(url, Duration.ofSeconds(10));
        JsonNode payload = JsonUtils.readTree(objectMapper, text);
        if (!payload.isObject()) {
            throw new IllegalArgumentException("B站接口返回了无效数据");
        }
        return payload;
    }

    /**
     * 解析 B 站短链的最终跳转地址。
     *
     * @param url 原始链接
     * @return 可继续解析的完整链接
     */
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

    /**
     * 从页面 HTML 中提取 `__INITIAL_STATE__` 数据。
     *
     * @param html 页面 HTML
     * @return 提取出的 JSON 节点
     */
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

    /**
     * 对文本执行 URL 编码。
     *
     * @param value 原始文本
     * @return 编码结果
     */
    public String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 去除 HTML 标签，保留纯文本。
     *
     * @param value 原始 HTML
     * @return 纯文本结果
     */
    public String stripHtml(String value) {
        return Jsoup.parse(value == null ? "" : value).text().trim();
    }

    /**
     * 读取页面 meta 标签内容。
     *
     * @param document 页面文档
     * @param selector meta 选择器
     * @return 标签内容
     */
    public String meta(Document document, String selector) {
        return document.select(selector).attr("content");
    }

    /**
     * 按正则提取首个匹配分组。
     *
     * @param text 原始文本
     * @param pattern 正则表达式
     * @return 匹配结果
     */
    public String extractRegex(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /**
     * 读取节点字段文本，为空时返回回退值。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @param fallback 回退值
     * @return 字段文本
     */
    public String value(JsonNode node, String fieldName, String fallback) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            String value = node.get(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * 将 JSON 节点转换为 Map 结构。
     *
     * @param node JSON 节点
     * @return 转换后的映射结果
     */
    public Map<String, Object> mapOf(JsonNode node) {
        return objectMapper.convertValue(
                node == null ? objectMapper.createObjectNode() : node,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
        );
    }

    /**
     * 将 JSON 节点安全转换为整数。
     *
     * @param node JSON 节点
     * @return 转换后的整数结果
     */
    public int safeInt(JsonNode node) {
        return TextUtils.safeInt(node == null ? null : node.asText(""));
    }
}
