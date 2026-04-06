package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.vectorstore.ChromaVectorStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LIMIT = 4;
    private static final int DEFAULT_SAMPLE_LIMIT = 10;

    private final ObjectMapper objectMapper;
    private final LocalEmbeddingService embeddingService;
    private final ChromaVectorStore chromaVectorStore;
    private final SemanticEmbeddingService semanticEmbeddingService;
    private final Path persistDirectory;
    private final Path fallbackStorePath;
    private final ReentrantLock fallbackLock = new ReentrantLock();

    public KnowledgeBaseService(
            AppProperties properties,
            ObjectMapper objectMapper,
            LocalEmbeddingService embeddingService,
            ChromaVectorStore chromaVectorStore,
            SemanticEmbeddingService semanticEmbeddingService
    ) {
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.chromaVectorStore = chromaVectorStore;
        this.semanticEmbeddingService = semanticEmbeddingService;
        this.persistDirectory = Path.of(properties.getVectorDbPath());
        this.fallbackStorePath = this.persistDirectory.resolve("bilibili_knowledge__fallback_store.json");
        try {
            Files.createDirectories(this.persistDirectory);
            if (Files.notExists(this.fallbackStorePath)) {
                writeRecords(List.of());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize local knowledge base storage", exception);
        }
    }

    public record KnowledgeDocument(String id, String text, Map<String, Object> metadata) {
    }

    public Map<String, Object> backendStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> chromaStatus = chromaVectorStore.backendStatus();

        payload.put("available", chromaVectorStore.isAvailable() || true); // fallback 总是可用
        payload.put("backend", chromaVectorStore.isAvailable() ? "chroma_http" : "json_fallback");
        payload.put("backend_detail", chromaStatus.getOrDefault("backend_detail", "java_local_json_vector_store"));
        payload.put("persist_directory", persistDirectory.toString());
        payload.put("collection_name", "bilibili_knowledge");
        payload.put("document_count", count());
        payload.put("init_error", chromaStatus.getOrDefault("init_error", ""));
        payload.put("embedding_provider", semanticEmbeddingService.getProvider());
        payload.put("embedding_model", semanticEmbeddingService.getModelName());
        payload.put("embedding_fallback", semanticEmbeddingService.isUsingFallback());
        payload.put("embedding_error", semanticEmbeddingService.getLoadError());
        payload.put("last_updated_at", lastUpdatedAt());
        payload.put("chroma_available", chromaVectorStore.isAvailable());
        payload.put("chroma_status", chromaStatus);
        return payload;
    }

    public int count() {
        return loadRecords().size();
    }

    public Map<String, Object> sample(int limit, int offset, Map<String, Object> metadataFilter) {
        int size = Math.max(1, Math.min(limit <= 0 ? DEFAULT_SAMPLE_LIMIT : limit, 50));
        int start = Math.max(0, offset);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : loadRecords()) {
            if (matchesMetadata(item, metadataFilter)) {
                filtered.add(item);
            }
        }
        List<Map<String, Object>> page = filtered.subList(Math.min(start, filtered.size()), Math.min(start + size, filtered.size()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : page) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", stringValue(item.get("document_id")));
            payload.put("text", stringValue(item.get("text")));
            payload.put("metadata", metadataOf(item));
            items.add(payload);
        }
        return Map.of(
                "items", items,
                "limit", size,
                "offset", start
        );
    }

    public boolean exists(String documentId, Map<String, Object> metadataFilter) {
        Map<String, Object> where = new LinkedHashMap<>();
        if (metadataFilter != null) {
            where.putAll(metadataFilter);
        }
        if (!stringValue(documentId).isBlank()) {
            where.put("document_id", stringValue(documentId));
        }
        for (Map<String, Object> item : loadRecords()) {
            if (matchesMetadata(item, where)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> delete(String documentId, Map<String, Object> metadataFilter) {
        Map<String, Object> where = new LinkedHashMap<>();
        if (metadataFilter != null) {
            where.putAll(metadataFilter);
        }
        if (!stringValue(documentId).isBlank()) {
            where.put("document_id", stringValue(documentId));
        }
        if (where.isEmpty()) {
            throw new IllegalArgumentException("Deleting knowledge documents requires document_id or metadata_filter.");
        }
        List<Map<String, Object>> existing = loadRecords();
        List<Map<String, Object>> kept = new ArrayList<>();
        int deletedCount = 0;
        for (Map<String, Object> item : existing) {
            if (matchesMetadata(item, where)) {
                deletedCount++;
            } else {
                kept.add(item);
            }
        }
        if (deletedCount > 0) {
            writeRecords(kept);
        }
        return Map.of("deleted_count", deletedCount, "where", where);
    }

    public Map<String, Object> addDocument(KnowledgeDocument document) {
        String documentId = stringValue(document.id());
        String text = stringValue(document.text());
        if (documentId.isBlank() || text.isBlank()) {
            return Map.of("status", "skipped", "document_id", documentId, "chunk_count", 0);
        }
        List<String> chunks = splitText(text, 320, 60);
        if (chunks.isEmpty()) {
            return Map.of("status", "skipped", "document_id", documentId, "chunk_count", 0);
        }
        boolean existed = exists(documentId, null);
        delete(documentId, null);
        List<Map<String, Object>> records = loadRecords();
        List<List<Double>> embeddings = embeddingService.embedDocuments(chunks);
        for (int index = 0; index < chunks.size(); index++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (document.metadata() != null) {
                metadata.putAll(document.metadata());
            }
            metadata.put("document_id", documentId);
            metadata.put("chunk_index", index);
            metadata.putIfAbsent("source", "knowledge_base");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", documentId + ":" + index);
            payload.put("document_id", documentId);
            payload.put("text", chunks.get(index));
            payload.put("metadata", metadata);
            payload.put("embedding", embeddings.get(index));
            records.add(payload);
        }
        writeRecords(records);
        return Map.of(
                "status", existed ? "updated" : "ok",
                "document_id", documentId,
                "chunk_count", chunks.size()
        );
    }

    public Map<String, Object> retrieve(String query, int limit, Map<String, Object> metadataFilter) {
        String cleanQuery = stringValue(query);
        if (cleanQuery.isBlank()) {
            return Map.of("query", cleanQuery, "matches", List.of());
        }
        int size = Math.max(1, limit <= 0 ? DEFAULT_LIMIT : limit);
        List<Double> queryVector = embeddingService.embedQuery(cleanQuery);
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> item : loadRecords()) {
            if (!matchesMetadata(item, metadataFilter)) {
                continue;
            }
            String text = stringValue(item.get("text"));
            double similarity = embeddingService.cosineSimilarity(queryVector, readEmbedding(item.get("embedding")));
            double lexicalBonus = embeddingService.lexicalOverlapScore(cleanQuery, text) * 0.2;
            double distance = Math.max(0.0, 1.0 - similarity - lexicalBonus);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", stringValue(item.get("document_id")));
            payload.put("text", text);
            payload.put("metadata", metadataOf(item));
            payload.put("score", distance);
            matches.add(payload);
        }
        matches.sort(Comparator
                .comparingDouble((Map<String, Object> item) -> doubleValue(item.get("score")))
                .thenComparing(item -> stringValue(item.get("id"))));
        return Map.of(
                "query", cleanQuery,
                "matches", matches.subList(0, Math.min(size, matches.size()))
        );
    }

    public List<Map<String, Object>> collapseMatches(List<Map<String, Object>> rawMatches) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : rawMatches) {
            String id = stringValue(item.get("id"));
            if (id.isBlank()) {
                continue;
            }
            Map<String, Object> existing = merged.get(id);
            if (existing == null || doubleValue(item.get("score")) < doubleValue(existing.get("score"))) {
                merged.put(id, new LinkedHashMap<>(item));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 从本地 JSON 文件加载知识库记录。
     * 使用锁保证并发安全，若文件不存在或解析失败返回空列表。
     * @return 知识库记录列表
     */
    private List<Map<String, Object>> loadRecords() {
        fallbackLock.lock();
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
            fallbackLock.unlock();
        }
    }

    /**
     * 将知识库记录持久化到本地 JSON 文件。
     * 使用原子操作（写临时文件再移动）保证数据一致性。
     * @param records 要写入的记录列表
     */
    private void writeRecords(List<Map<String, Object>> records) {
        fallbackLock.lock();
        try {
            Files.createDirectories(persistDirectory);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("collection_name", "bilibili_knowledge");
            payload.put("updated_at", DATETIME_FORMATTER.format(LocalDateTime.now()));
            payload.put("items", records);
            Path tempPath = fallbackStorePath.resolveSibling(fallbackStorePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), payload);
            Files.move(tempPath, fallbackStorePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist local knowledge base records", exception);
        } finally {
            fallbackLock.unlock();
        }
    }

    /**
     * 检查记录是否匹配元数据过滤条件。
     * document_id 单独比较，其他字段从 metadata 中取值比较。
     * @param record 知识库记录
     * @param metadataFilter 元数据过滤条件
     * @return 是否匹配
     */
    private boolean matchesMetadata(Map<String, Object> record, Map<String, Object> metadataFilter) {
        if (metadataFilter == null || metadataFilter.isEmpty()) {
            return true;
        }
        Map<String, Object> metadata = metadataOf(record);
        String documentId = stringValue(record.get("document_id"));
        for (Map.Entry<String, Object> entry : metadataFilter.entrySet()) {
            if (Objects.equals("document_id", entry.getKey())) {
                if (!Objects.equals(documentId, stringValue(entry.getValue()))) {
                    return false;
                }
                continue;
            }
            if (!Objects.equals(stringValue(metadata.get(entry.getKey())), stringValue(entry.getValue()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从记录中提取 metadata 字段。
     * @param record 知识库记录
     * @return metadata Map，若不存在则返回空 Map
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
     * 将对象安全转换为向量列表。
     * @param raw 原始对象
     * @return Double 列表，若转换失败返回空列表
     */
    private List<Double> readEmbedding(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Double> embedding = new ArrayList<>(list.size());
        for (Object item : list) {
            embedding.add(doubleValue(item));
        }
        return embedding;
    }

    /**
     * 将长文本按指定大小和重叠字符数切分成块。
     * @param text 原始文本
     * @param chunkSize 每块最大字符数
     * @param overlap 块间重叠字符数
     * @return 文本块列表
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        String clean = stringValue(text);
        if (clean.isBlank()) {
            return List.of();
        }
        if (clean.length() <= chunkSize) {
            return List.of(clean);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < clean.length()) {
            int end = Math.min(clean.length(), start + chunkSize);
            String chunk = clean.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= clean.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    /**
     * 获取知识库最近更新时间。
     * 遍历存储目录下所有文件，返回最新修改时间对应的格式化字符串。
     * @return 格式化的时间字符串，若无文件则返回空字符串
     */
    private String lastUpdatedAt() {
        try (Stream<Path> stream = Files.walk(persistDirectory)) {
            return stream
                    .filter(Files::exists)
                    .map(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        } catch (IOException exception) {
                            return Instant.EPOCH;
                        }
                    })
                    .max(Comparator.naturalOrder())
                    .map(instant -> DATETIME_FORMATTER.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault())))
                    .orElse("");
        } catch (IOException exception) {
            return "";
        }
    }

    /**
     * 安全转换为字符串，去除首尾空白。
     * @param value 原始值
     * @return 字符串结果，null 转空字符串
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 安全转换为 double。
     * @param value 原始值
     * @return double 值，转换失败返回 0.0
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
