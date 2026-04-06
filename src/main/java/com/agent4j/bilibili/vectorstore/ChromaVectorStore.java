package com.agent4j.bilibili.vectorstore;

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
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Chroma 向量存储 HTTP 客户端。
 * 通过 Chroma Server API 与 Chroma 服务通信。
 * 对标 Python 版本的 chromadb 集成。
 */
@Component
public class ChromaVectorStore {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String collectionName;
    private boolean isAvailable = false;
    private String initError = "";
    private String backendDetail = "";

    public ChromaVectorStore(AppProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.collectionName = "bilibili_knowledge";

        // 从配置读取 Chroma 服务器地址
        String chromaHost = properties.getChromaHost() != null ? properties.getChromaHost() : DEFAULT_HOST;
        int chromaPort = properties.getChromaPort() > 0 ? properties.getChromaPort() : DEFAULT_PORT;
        this.baseUrl = "http://" + chromaHost + ":" + chromaPort;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 尝试初始化
        try {
            initialize();
        } catch (Exception e) {
            this.initError = e.getMessage();
            this.backendDetail = "Chroma server not available: " + e.getMessage();
        }
    }

    /**
     * 初始化 Chroma 连接。
     */
    private void initialize() throws IOException, InterruptedException {
        // 检查 Heartbeat 端点
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/heartbeat"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                this.isAvailable = true;
                this.backendDetail = "chroma_http";
            }
        } catch (Exception e) {
            this.isAvailable = false;
            throw new IOException("Chroma heartbeat failed: " + e.getMessage());
        }
    }

    /**
     * 检查是否可用。
     */
    public boolean isAvailable() {
        return isAvailable;
    }

    /**
     * 获取后端状态信息。
     */
    public Map<String, Object> backendStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", isAvailable);
        status.put("backend", isAvailable ? "chroma_http" : "disabled");
        status.put("backend_detail", backendDetail);
        status.put("base_url", baseUrl);
        status.put("collection_name", collectionName);
        status.put("init_error", initError);
        return status;
    }

    /**
     * 创建或获取 Collection。
     */
    public void createCollectionIfNotExists() throws IOException, InterruptedException {
        if (!isAvailable) {
            throw new IOException("Chroma not available");
        }

        // 先尝试获取 collection
        String collectionId = getCollectionId(collectionName);
        if (collectionId != null) {
            return; // 已存在
        }

        // 创建新 collection
        String jsonBody = objectMapper.writeValueAsString(Map.of("name", collectionName));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1 collections"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201 && response.statusCode() != 409) {
            throw new IOException("Failed to create collection: " + response.body());
        }
    }

    /**
     * 获取 Collection ID。
     */
    private String getCollectionId(String name) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/collections/" + name))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
            return (String) data.get("id");
        }
        return null;
    }

    /**
     * 添加文档到 Collection。
     */
    public void addDocuments(List<String> ids, List<String> documents, List<Map<String, Object>> metadatas, List<List<Double>> embeddings)
            throws IOException, InterruptedException {

        if (!isAvailable) {
            throw new IOException("Chroma not available");
        }

        String collectionId = getCollectionId(collectionName);
        if (collectionId == null) {
            createCollectionIfNotExists();
            collectionId = getCollectionId(collectionName);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ids", ids);
        payload.put("documents", documents);
        payload.put("metadatas", metadatas);
        payload.put("embeddings", embeddings);

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/collections/" + collectionId + "/add"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IOException("Failed to add documents: " + response.body());
        }
    }

    /**
     * 查询相似文档。
     */
    public List<Map<String, Object>> query(List<Double> queryEmbedding, int limit, Map<String, Object> whereFilter)
            throws IOException, InterruptedException {

        if (!isAvailable) {
            throw new IOException("Chroma not available");
        }

        String collectionId = getCollectionId(collectionName);
        if (collectionId == null) {
            return List.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query_embeddings", List.of(queryEmbedding));
        payload.put("n_results", limit);
        payload.put("where", whereFilter);
        payload.put("include", List.of("documents", "metadatas", "distances"));

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/collections/" + collectionId + "/query"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Query failed: " + response.body());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

        List<Map<String, Object>> matches = new ArrayList<>();
        List<List<String>> resultIds = getNestedList(result, "ids");
        List<List<String>> resultDocs = getNestedList(result, "documents");
        List<List<Map<String, Object>>> resultMetadatas = getNestedListObjects(result, "metadatas");
        List<List<Double>> resultDistances = getNestedListDoubles(result, "distances");

        if (!resultIds.isEmpty() && !resultDocs.isEmpty()) {
            for (int i = 0; i < resultIds.get(0).size(); i++) {
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("id", resultIds.get(0).get(i));
                match.put("text", resultDocs.get(0).get(i));
                match.put("metadata", resultMetadatas.isEmpty() || resultMetadatas.get(0).isEmpty() ? Map.of() : resultMetadatas.get(0).get(i));
                match.put("distance", resultDistances.isEmpty() || resultDistances.get(0).isEmpty() ? 0.0 : resultDistances.get(0).get(i));
                matches.add(match);
            }
        }

        return matches;
    }

    /**
     * 删除文档。
     */
    public void delete(String whereFilter) throws IOException, InterruptedException {
        if (!isAvailable) {
            throw new IOException("Chroma not available");
        }

        String collectionId = getCollectionId(collectionName);
        if (collectionId == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("where", whereFilter);

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/collections/" + collectionId + "/delete"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Delete failed: " + response.body());
        }
    }

    /**
     * 获取 Collection 中的文档数量。
     */
    public int count() throws IOException, InterruptedException {
        if (!isAvailable) {
            return 0;
        }

        String collectionId = getCollectionId(collectionName);
        if (collectionId == null) {
            return 0;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/collections/" + collectionId + "/count"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return Integer.parseInt(response.body().trim());
        }
        return 0;
    }

    /**
     * 获取嵌套的 List<String>。
     */
    @SuppressWarnings("unchecked")
    private List<List<String>> getNestedList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty() && list.get(0) instanceof List<?>) {
                return (List<List<String>>) value;
            } else if (!list.isEmpty() && list.get(0) instanceof String) {
                return List.of((List<String>) value);
            }
        }
        return List.of();
    }

    /**
     * 获取嵌套的 List<Map>。
     */
    @SuppressWarnings("unchecked")
    private List<List<Map<String, Object>>> getNestedListObjects(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty() && list.get(0) instanceof List<?>) {
                return (List<List<Map<String, Object>>>) value;
            } else if (!list.isEmpty() && list.get(0) instanceof Map) {
                return List.of((List<Map<String, Object>>) value);
            }
        }
        return List.of();
    }

    /**
     * 获取嵌套的 List<Double>。
     */
    @SuppressWarnings("unchecked")
    private List<List<Double>> getNestedListDoubles(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty() && list.get(0) instanceof List<?>) {
                return (List<List<Double>>) value;
            } else if (!list.isEmpty() && list.get(0) instanceof Number) {
                return List.of((List<Double>) value);
            }
        }
        return List.of();
    }
}
