package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * 聊天会话持久化服务。
 * 基于本地 JSON 文件存储，支持页面刷新后会话恢复。
 * 对标 Python 版本的 ChatSessionMetadataStore。
 */
@Service
public class ChatSessionService {

    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final String SESSIONS_DIR_NAME = "chat_sessions";
    private static final String SESSIONS_INDEX_FILE = "sessions_index.json";
    private static final String SESSION_META_SUFFIX = "_meta.json";
    private static final String SESSION_HISTORY_SUFFIX = "_history.json";

    private final ObjectMapper objectMapper;
    private final Path sessionsDir;
    private final ReentrantLock indexLock = new ReentrantLock();
    private final Map<String, Map<String, Object>> memoryCache = new ConcurrentHashMap<>();
    private final int maxCacheEntries;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    public ChatSessionService(AppProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.sessionsDir = Path.of(properties.getVectorDbPath()).resolve(SESSIONS_DIR_NAME);
        this.maxCacheEntries = 100;
        initSessionsDirectory();
    }

    private void initSessionsDirectory() {
        try {
            Files.createDirectories(sessionsDir);
            Path indexFile = indexPath();
            if (Files.notExists(indexFile)) {
                writeIndex(List.of());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize sessions directory", e);
        }
    }

    private Path indexPath() {
        return sessionsDir.resolve(SESSIONS_INDEX_FILE);
    }

    private Path metaPath(String sessionId) {
        return sessionsDir.resolve(sessionId + SESSION_META_SUFFIX);
    }

    private Path historyPath(String sessionId) {
        return sessionsDir.resolve(sessionId + SESSION_HISTORY_SUFFIX);
    }

    /**
     * 生成新的会话 ID。
     */
    public String generateSessionId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 保存会话（同步）。
     */
    public void saveSession(String sessionId, String firstQuestion, List<Map<String, String>> history) {
        long now = System.currentTimeMillis() / 1000;
        long createdAt = now;

        // 尝试从现有元数据获取创建时间
        Map<String, Object> existing = loadMeta(sessionId);
        if (existing != null) {
            Object created = existing.get("created_at");
            if (created instanceof Number) {
                createdAt = ((Number) created).longValue();
            }
        }

        // 如果没有传入 first_question，从历史中提取第一条用户消息
        if (firstQuestion == null || firstQuestion.isBlank()) {
            for (Map<String, String> item : history) {
                String role = item.getOrDefault("role", "");
                String content = item.getOrDefault("content", "");
                if ("user".equals(role) && !content.isBlank()) {
                    firstQuestion = content.length() > 200 ? content.substring(0, 200) : content;
                    break;
                }
            }
        }

        // 构建元数据
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("session_id", sessionId);
        meta.put("created_at", createdAt);
        meta.put("created_at_display", formatDisplayTime(createdAt));
        meta.put("first_question", firstQuestion != null ? firstQuestion : "");
        meta.put("updated_at", now);
        meta.put("updated_at_display", formatDisplayTime(now));
        meta.put("message_count", history.size());

        // 构建历史数据
        Map<String, Object> historyData = new LinkedHashMap<>();
        historyData.put("session_id", sessionId);
        historyData.put("created_at", createdAt);
        historyData.put("history", history);

        // 写入文件
        try {
            writeJson(metaPath(sessionId), meta);
            writeJson(historyPath(sessionId), historyData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save session: " + sessionId, e);
        }

        // 更新索引
        upsertIndex(sessionId, meta);

        // 更新内存缓存
        memoryCache.put(sessionId, meta);
        pruneCache();
    }

    /**
     * 异步保存会话。
     */
    public void saveSessionAsync(String sessionId, String firstQuestion, List<Map<String, String>> history) {
        executor.submit(() -> {
            try {
                saveSession(sessionId, firstQuestion, history);
            } catch (Exception e) {
                // 静默处理异步保存失败
            }
        });
    }

    /**
     * 列出所有会话（按 updated_at 倒序）。
     */
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> index = loadIndex();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : index) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("session_id", item.getOrDefault("session_id", ""));
            entry.put("created_at", item.getOrDefault("created_at", 0L));
            entry.put("created_at_display", item.getOrDefault("created_at_display", ""));
            entry.put("first_question", item.getOrDefault("first_question", ""));
            entry.put("updated_at", item.getOrDefault("updated_at", 0L));
            entry.put("updated_at_display", item.getOrDefault("updated_at_display", ""));
            entry.put("message_count", item.getOrDefault("message_count", 0));
            result.add(entry);
        }
        return result;
    }

    /**
     * 获取指定会话的完整信息。
     */
    public Map<String, Object> getSession(String sessionId) {
        Map<String, Object> meta = loadMeta(sessionId);
        if (meta == null) {
            return null;
        }

        List<Map<String, String>> history = loadHistory(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", meta.getOrDefault("session_id", sessionId));
        result.put("created_at", meta.getOrDefault("created_at", 0L));
        result.put("created_at_display", meta.getOrDefault("created_at_display", ""));
        result.put("first_question", meta.getOrDefault("first_question", ""));
        result.put("updated_at", meta.getOrDefault("updated_at", 0L));
        result.put("updated_at_display", meta.getOrDefault("updated_at_display", ""));
        result.put("history", history);
        return result;
    }

    /**
     * 删除指定会话。
     */
    public void deleteSession(String sessionId) {
        try {
            Files.deleteIfExists(metaPath(sessionId));
            Files.deleteIfExists(historyPath(sessionId));
        } catch (IOException ignored) {
        }

        // 从索引中移除
        indexLock.lock();
        try {
            List<Map<String, Object>> index = loadIndex();
            index.removeIf(item -> sessionId.equals(item.get("session_id")));
            writeIndex(index);
        } finally {
            indexLock.unlock();
        }

        // 从缓存中移除
        memoryCache.remove(sessionId);
    }

    /**
     * 加载指定会话的元数据。
     */
    private Map<String, Object> loadMeta(String sessionId) {
        // 先检查缓存
        Map<String, Object> cached = memoryCache.get(sessionId);
        if (cached != null) {
            return cached;
        }

        Path path = metaPath(sessionId);
        if (Files.notExists(path)) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = objectMapper.readValue(path.toFile(), Map.class);
            memoryCache.put(sessionId, meta);
            return meta;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 加载指定会话的历史。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadHistory(String sessionId) {
        Path path = historyPath(sessionId);
        if (Files.notExists(path)) {
            return List.of();
        }

        try {
            Map<String, Object> data = objectMapper.readValue(path.toFile(), Map.class);
            Object history = data.get("history");
            if (history instanceof List<?>) {
                return (List<Map<String, String>>) history;
            }
            return List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 加载会话索引。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadIndex() {
        Path path = indexPath();
        if (Files.notExists(path)) {
            return List.of();
        }

        try {
            Object data = objectMapper.readValue(path.toFile(), Object.class);
            if (data instanceof List<?>) {
                return (List<Map<String, Object>>) data;
            }
            return List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 写入索引文件。
     */
    private void writeIndex(List<Map<String, Object>> index) {
        try {
            writeJson(indexPath(), index);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write sessions index", e);
        }
    }

    /**
     * 更新索引（保持 updated_at 倒序）。
     */
    private void upsertIndex(String sessionId, Map<String, Object> meta) {
        indexLock.lock();
        try {
            List<Map<String, Object>> index = loadIndex();
            index.removeIf(item -> sessionId.equals(item.get("session_id")));
            index.add(0, meta); // 最新更新的放最前
            writeIndex(index);
        } finally {
            indexLock.unlock();
        }
    }

    /**
     * 写入 JSON 文件（原子操作）。
     */
    private void writeJson(Path path, Object data) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), data);
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * 清理过期缓存项。
     */
    private void pruneCache() {
        if (memoryCache.size() <= maxCacheEntries) {
            return;
        }
        // 简单策略：保留最近访问的
        var iterator = memoryCache.entrySet().iterator();
        int removed = 0;
        int toRemove = memoryCache.size() - maxCacheEntries;
        while (iterator.hasNext() && removed < toRemove) {
            iterator.next();
            iterator.remove();
            removed++;
        }
    }

    /**
     * 格式化时间为显示格式。
     */
    private String formatDisplayTime(long timestamp) {
        return DISPLAY_TIME_FORMATTER.format(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()));
    }

    /**
     * 验证会话 ID 是否合法。
     */
    public boolean isValidSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        // 允许字母、数字、点、下划线、冒号、连字符，长度 1-128
        return sessionId.matches("^[0-9A-Za-z][0-9A-Za-z._:-]{0,127}$");
    }

    /**
     * 规范化聊天历史。
     */
    public List<Map<String, String>> normalizeHistory(List<?> rawHistory, int limit) {
        if (rawHistory == null) {
            return List.of();
        }

        int maxItems = Math.max(1, limit);
        List<Map<String, String>> result = new ArrayList<>();
        List<String> validRoles = new ArrayList<>();
        validRoles.add("user");
        validRoles.add("assistant");
        validRoles.add("system");

        for (Object item : rawHistory) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object roleObj = rawMap.get("role");
            Object contentObj = rawMap.get("content");
            String role = (roleObj != null ? String.valueOf(roleObj) : "").trim().toLowerCase();
            String content = (contentObj != null ? String.valueOf(contentObj) : "").trim();

            if (!validRoles.contains(role) || content.isBlank()) {
                continue;
            }

            Map<String, String> normalized = new LinkedHashMap<>();
            normalized.put("role", role);
            normalized.put("content", content);

            // 保留 actions 和 references
            Object actionsObj = rawMap.get("actions");
            if (actionsObj instanceof List<?> actions) {
                normalized.put("actions", objectMapper.valueToTree(actions).toString());
            }
            Object refsObj = rawMap.get("references");
            if (refsObj instanceof List<?> references) {
                normalized.put("references", objectMapper.valueToTree(references).toString());
            }

            result.add(normalized);
        }

        // 只保留最新的 limit 条
        int size = result.size();
        if (size > maxItems) {
            return result.subList(size - maxItems, size);
        }
        return result;
    }
}
