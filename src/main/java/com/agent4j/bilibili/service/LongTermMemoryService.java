package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class LongTermMemoryService {

    private final ObjectMapper objectMapper;
    private final LocalEmbeddingService embeddingService;
    private final Path persistDirectory;
    private final Path fallbackStorePath;
    private final ReentrantLock lock = new ReentrantLock();

    public LongTermMemoryService(
            AppProperties properties,
            ObjectMapper objectMapper,
            LocalEmbeddingService embeddingService
    ) {
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.persistDirectory = Path.of(properties.getVectorDbPath());
        this.fallbackStorePath = this.persistDirectory.resolve("user_long_term_memory__fallback_store.json");
        try {
            Files.createDirectories(this.persistDirectory);
            if (Files.notExists(this.fallbackStorePath)) {
                writeRecords(List.of());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize long-term memory store", exception);
        }
    }

    public String backend() {
        return "json_fallback";
    }

    public String collectionName() {
        return "user_long_term_memory";
    }

    public Map<String, Object> saveUserData(String userId, Map<String, Object> data, String memoryType) {
        String cleanUserId = normalizeUserId(userId);
        String cleanMemoryType = stringValue(memoryType).isBlank() ? "workspace_record" : stringValue(memoryType);
        String text;
        try {
            text = objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize memory payload", exception);
        }

        List<Map<String, Object>> records = loadRecords();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("user_id", cleanUserId);
        metadata.put("memory_type", cleanMemoryType);
        metadata.put("created_at", Instant.now().getEpochSecond());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payload.put("text", text);
        payload.put("metadata", metadata);
        payload.put("embedding", embeddingService.embedQuery(text));
        records.add(payload);
        writeRecords(records);
        return Map.of(
                "status", "ok",
                "user_id", cleanUserId,
                "record_id", payload.get("id")
        );
    }

    public Map<String, Object> retrieveUserHistory(String userId, String query, int limit) {
        String cleanUserId = normalizeUserId(userId);
        String cleanQuery = stringValue(query);
        if (cleanQuery.isBlank()) {
            return Map.of("user_id", cleanUserId, "history", List.of());
        }
        int size = Math.max(1, limit <= 0 ? 4 : limit);
        List<Double> queryVector = embeddingService.embedQuery(cleanQuery);
        List<Map<String, Object>> history = new ArrayList<>();
        for (Map<String, Object> item : loadRecords()) {
            Map<String, Object> metadata = metadataOf(item);
            if (!cleanUserId.equals(stringValue(metadata.get("user_id")))) {
                continue;
            }
            String text = stringValue(item.get("text"));
            double similarity = embeddingService.cosineSimilarity(queryVector, readEmbedding(item.get("embedding")));
            double lexicalBonus = embeddingService.lexicalOverlapScore(cleanQuery, text) * 0.2;
            double distance = Math.max(0.0, 1.0 - similarity - lexicalBonus);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", text);
            payload.put("metadata", metadata);
            payload.put("score", distance);
            history.add(payload);
        }
        history.sort(Comparator.comparingDouble(item -> doubleValue(item.get("score"))));
        return Map.of(
                "user_id", cleanUserId,
                "history", history.subList(0, Math.min(size, history.size()))
        );
    }

    /**
     * 从本地 JSON 文件加载长期记忆记录。
     *
     * @return 记录列表
     */
    private List<Map<String, Object>> loadRecords() {
        lock.lock();
        try {
            if (Files.notExists(fallbackStorePath)) {
                writeRecords(List.of());
            }
            byte[] bytes = Files.readAllBytes(fallbackStorePath);
            if (bytes.length == 0) {
                return new ArrayList<>();
            }
            Map<String, Object> payload = objectMapper.readValue(bytes, new TypeReference<>() {
            });
            Object items = payload.get("items");
            if (!(items instanceof List<?> list)) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> records = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> source) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : source.entrySet()) {
                        record.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    records.add(record);
                }
            }
            return records;
        } catch (IOException exception) {
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将记录持久化到本地 JSON 文件。
     *
     * @param records 要写入的记录列表
     */
    private void writeRecords(List<Map<String, Object>> records) {
        lock.lock();
        try {
            Files.createDirectories(persistDirectory);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("collection_name", collectionName());
            payload.put("updated_at", Instant.now().getEpochSecond());
            payload.put("items", records);
            Path tempPath = fallbackStorePath.resolveSibling(fallbackStorePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), payload);
            Files.move(tempPath, fallbackStorePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist long-term memory records", exception);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从记录中提取 metadata 字段。
     *
     * @param record 知识库记录
     * @return metadata Map
     */
    private Map<String, Object> metadataOf(Map<String, Object> record) {
        Object metadata = record.get("metadata");
        if (!(metadata instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * 安全将对象转换为向量列表。
     *
     * @param raw 原始对象
     * @return Double 列表
     */
    private List<Double> readEmbedding(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Double> embedding = new ArrayList<>();
        for (Object item : list) {
            embedding.add(doubleValue(item));
        }
        return embedding;
    }

    /**
     * 规范化用户 ID，空值转为 anonymous。
     *
     * @param userId 用户 ID
     * @return 规范化后的用户 ID
     */
    private String normalizeUserId(String userId) {
        String clean = stringValue(userId);
        return clean.isBlank() ? "anonymous" : clean;
    }

    /**
     * 安全转换为字符串。
     *
     * @param value 原始值
     * @return 字符串结果
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 安全转换为 double。
     *
     * @param value 原始值
     * @return double 值
     */
    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(stringValue(value));
        } catch (Exception exception) {
            return 0.0;
        }
    }
}
