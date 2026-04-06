package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 语义嵌入服务。
 * 支持调用远程 Python embedding 服务（如 sentence-transformers）生成真实语义向量。
 * 当远程服务不可用时，自动回退到本地 deterministic 嵌入。
 * 对标 Python 版本的 SemanticEmbeddings。
 */
@Service
public class SemanticEmbeddingService {

    private final LocalEmbeddingService localEmbeddingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String embeddingServiceUrl;
    private final String modelName;
    private boolean usingFallback = false;
    private String provider = "semantic_http";
    private String loadError = "";

    public SemanticEmbeddingService(
            LocalEmbeddingService localEmbeddingService,
            AppProperties properties,
            ObjectMapper objectMapper
    ) {
        this.localEmbeddingService = localEmbeddingService;
        this.objectMapper = objectMapper;
        this.modelName = properties.getEmbeddingModelName() != null
                ? properties.getEmbeddingModelName()
                : "BAAI/bge-small-zh-v1.5";
        this.embeddingServiceUrl = properties.getEmbeddingServiceUrl() != null
                ? properties.getEmbeddingServiceUrl()
                : "http://localhost:8001";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 尝试连接远程服务
        try {
            checkRemoteService();
        } catch (Exception e) {
            this.usingFallback = true;
            this.provider = "deterministic_fallback";
            this.loadError = "Semantic embedding service unavailable: " + e.getMessage();
        }
    }

    /**
     * 检查远程 embedding 服务是否可用。
     * 向远程服务的 /health 端点发送请求，根据返回状态判断是否正常。
     *
     * @throws IOException 网络 IO 异常
     * @throws InterruptedException 请求被中断
     */
    private void checkRemoteService() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingServiceUrl + "/health"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            this.usingFallback = false;
            this.provider = "semantic_http";
            this.loadError = "";
        } else {
            throw new IOException("Service returned status: " + response.statusCode());
        }
    }

    /**
     * 批量嵌入文档。
     */
    public List<List<Double>> embedDocuments(List<String> texts) {
        if (usingFallback) {
            return localEmbeddingService.embedDocuments(texts);
        }

        try {
            return embedDocumentsRemote(texts);
        } catch (Exception e) {
            this.usingFallback = true;
            this.provider = "deterministic_fallback";
            this.loadError = "Remote embedding failed: " + e.getMessage();
            return localEmbeddingService.embedDocuments(texts);
        }
    }

    /**
     * 嵌入单个查询。
     */
    public List<Double> embedQuery(String text) {
        if (usingFallback) {
            return localEmbeddingService.embedQuery(text);
        }

        try {
            List<List<Double>> results = embedDocumentsRemote(List.of(text));
            return results.isEmpty() ? localEmbeddingService.embedQuery(text) : results.get(0);
        } catch (Exception e) {
            this.usingFallback = true;
            this.provider = "deterministic_fallback";
            this.loadError = "Remote embedding failed: " + e.getMessage();
            return localEmbeddingService.embedQuery(text);
        }
    }

    /**
     * 调用远程 embedding 服务生成向量。
     *
     * @param texts 文本列表
     * @return 向量列表
     * @throws IOException 网络 IO 异常
     * @throws InterruptedException 请求被中断
     */
    private List<List<Double>> embedDocumentsRemote(List<String> texts) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputs", texts);
        payload.put("normalize", true);

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingServiceUrl + "/embed"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Embedding service error: " + response.statusCode());
        }

        @SuppressWarnings("unchecked")
        List<List<Double>> results = objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, ArrayList.class));

        return results;
    }

    /**
     * 获取嵌入维度。
     */
    public int dimension() {
        if (usingFallback) {
            return localEmbeddingService.dimension();
        }

        try {
            List<Double> test = embedQuery("dimension check");
            return test.size();
        } catch (Exception e) {
            return localEmbeddingService.dimension();
        }
    }

    /**
     * 是否使用回退（deterministic）模式。
     */
    public boolean isUsingFallback() {
        return usingFallback;
    }

    /**
     * 获取嵌入服务提供商。
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 获取模型名称。
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取加载错误信息。
     */
    public String getLoadError() {
        return loadError;
    }

    /**
     * 获取完整状态信息。
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", provider);
        status.put("model_name", modelName);
        status.put("using_fallback", usingFallback);
        status.put("load_error", loadError);
        status.put("service_url", embeddingServiceUrl);
        status.put("dimension", dimension());
        return status;
    }
}
