package com.agent4j.bilibili.service;

import com.agent4j.bilibili.model.VideoMetrics;
import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSyncService {

    public static final List<String> SUPPORTED_UPLOAD_TYPES = List.of(".txt", ".md", ".docx", ".pdf");
    public static final Set<String> SUPPORTED_UPLOAD_SUFFIXES = Set.copyOf(SUPPORTED_UPLOAD_TYPES);

    private final TopicDataService topicDataService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeFileTextExtractor knowledgeFileTextExtractor;
    private final ObjectMapper objectMapper;

    public KnowledgeSyncService(
            TopicDataService topicDataService,
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeFileTextExtractor knowledgeFileTextExtractor,
            ObjectMapper objectMapper
    ) {
        this.topicDataService = topicDataService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeFileTextExtractor = knowledgeFileTextExtractor;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildKnowledgeBaseStatus(LongTermMemoryService longTermMemoryService, Map<String, Object> activeJob) {
        Map<String, Object> payload = new LinkedHashMap<>(knowledgeBaseService.backendStatus());
        payload.put("vector_db_path", payload.get("persist_directory"));
        payload.put("supported_upload_types", SUPPORTED_UPLOAD_TYPES);
        payload.put("memory_backend", longTermMemoryService.backend());
        payload.put("memory_collection", longTermMemoryService.collectionName());
        payload.put("active_update_job", activeJob);
        return payload;
    }

    public Map<String, Object> ingestUploadedFile(String filename, byte[] rawBytes, Map<String, Object> metadata) {
        String cleanFilename = Path.of(filename == null ? "" : filename).getFileName().toString();
        String suffix = suffix(cleanFilename);
        if (!SUPPORTED_UPLOAD_SUFFIXES.contains(suffix)) {
            throw new IllegalArgumentException("Only txt, md, docx and pdf knowledge files are currently supported.");
        }

        String text = knowledgeFileTextExtractor.extractText(cleanFilename, rawBytes).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Uploaded file is empty or contains no readable text.");
        }

        String contentHash = Integer.toHexString(text.hashCode());
        knowledgeBaseService.delete(null, Map.of("source", "uploaded_file", "content_hash", contentHash));

        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        mergedMetadata.put("source", "uploaded_file");
        mergedMetadata.put("filename", cleanFilename);
        mergedMetadata.put("file_type", suffix);
        mergedMetadata.put("content_hash", contentHash);
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }

        Map<String, Object> result = knowledgeBaseService.addDocument(
                new KnowledgeBaseService.KnowledgeDocument("file:" + contentHash, text, mergedMetadata)
        );
        Map<String, Object> payload = new LinkedHashMap<>(result);
        payload.put("filename", cleanFilename);
        payload.put("file_type", suffix);
        payload.put("content_hash", contentHash);
        return payload;
    }

    public Map<String, Object> updateKnowledgeBase(int perBoardLimit, Consumer<Map<String, Object>> progressCallback) {
        int limit = Math.max(1, Math.min(perBoardLimit <= 0 ? 10 : perBoardLimit, 20));
        List<BoardSpec> boards = List.of(
                new BoardSpec("全站热门榜", "all", ""),
                new BoardSpec("知识", "knowledge", "knowledge"),
                new BoardSpec("科技", "tech", "tech"),
                new BoardSpec("生活", "life", "life"),
                new BoardSpec("游戏", "game", "game"),
                new BoardSpec("娱乐", "ent", "ent")
        );

        List<BoardBatch> batches = boards.stream()
                .map(board -> new BoardBatch(board, fetchBoardVideos(board).stream().limit(limit).toList()))
                .toList();

        int totalBoards = boards.size();
        int totalItems = batches.stream().mapToInt(batch -> batch.videos().size()).sum();
        int processedBoards = 0;
        int processedItems = 0;
        int added = 0;
        int updated = 0;
        List<Map<String, Object>> boardSummaries = new ArrayList<>();

        notify(progressCallback, Map.of(
                "status", "running",
                "message", "Starting knowledge base refresh.",
                "percent", progressPercent(0, totalItems, 0, totalBoards),
                "processed_boards", processedBoards,
                "total_boards", totalBoards,
                "processed_items", processedItems,
                "total_items", totalItems
        ));

        for (BoardBatch batch : batches) {
            BoardSpec board = batch.board();
            List<VideoMetrics> videos = batch.videos();
            int boardAdded = 0;
            int boardUpdated = 0;
            int boardIndex = 0;

            for (VideoMetrics video : videos) {
                boardIndex++;
                processedItems++;

                String documentId = buildDocumentId(board, video);
                boolean existed = knowledgeBaseService.exists(documentId, null);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("source", "bilibili_hot_sync");
                metadata.put("board_type", board.label());
                metadata.put("partition", board.partition());
                metadata.put("bvid", video.getBvid());
                metadata.put("author", video.getAuthor());
                metadata.put("updated_at", Instant.now().getEpochSecond());

                knowledgeBaseService.addDocument(
                        new KnowledgeBaseService.KnowledgeDocument(
                                documentId,
                                buildStructuredVideoText(board, video),
                                metadata
                        )
                );

                if (existed) {
                    updated++;
                    boardUpdated++;
                } else {
                    added++;
                    boardAdded++;
                }

                Map<String, Object> progress = new LinkedHashMap<>();
                progress.put("status", "running");
                progress.put("message", "Syncing " + board.label() + " item " + boardIndex + "/" + videos.size());
                progress.put("board", board.label());
                progress.put("board_type", board.label());
                progress.put("current_title", video.getTitle());
                progress.put("percent", progressPercent(processedItems, totalItems, processedBoards, totalBoards));
                progress.put("processed_boards", processedBoards);
                progress.put("total_boards", totalBoards);
                progress.put("processed_items", processedItems);
                progress.put("total_items", totalItems);
                progress.put("board_items", videos.size());
                notify(progressCallback, progress);
            }

            processedBoards++;
            boardSummaries.add(Map.of(
                    "board", board.label(),
                    "board_type", board.label(),
                    "count", videos.size(),
                    "added", boardAdded,
                    "updated", boardUpdated,
                    "saved_count", boardAdded + boardUpdated,
                    "updated_count", boardUpdated,
                    "failed", List.of()
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("added", added);
        result.put("updated", updated);
        result.put("total_saved", added + updated);
        result.put("total_updated", updated);
        result.put("total_failed", 0);
        result.put("boards", boardSummaries);
        result.put("document_count", knowledgeBaseService.count());

        notify(progressCallback, Map.of(
                "status", "completed",
                "message", "Knowledge base refresh completed.",
                "percent", 100,
                "processed_boards", totalBoards,
                "total_boards", totalBoards,
                "processed_items", totalItems,
                "total_items", totalItems,
                "result", result
        ));
        return result;
    }

    public List<Map<String, Object>> collapseKnowledgeMatches(List<Map<String, Object>> matches) {
        return knowledgeBaseService.collapseMatches(matches);
    }

    private List<VideoMetrics> fetchBoardVideos(BoardSpec board) {
        return switch (board.boardType()) {
            case "all" -> topicDataService.fetchHotVideos();
            default -> topicDataService.fetchPartitionVideos(board.partition());
        };
    }

    private String buildDocumentId(BoardSpec board, VideoMetrics video) {
        String bvid = TextUtils.cleanCopyText(video.getBvid());
        if (bvid.isBlank()) {
            bvid = Integer.toHexString((board.label() + ":" + video.getTitle()).hashCode());
        }
        return ("all".equals(board.boardType()) ? "hot:" : "partition:" + board.partition() + ":") + bvid;
    }

    private String buildStructuredVideoText(BoardSpec board, VideoMetrics video) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("榜单来源", board.label());
        payload.put("视频标题", video.getTitle());
        payload.put("UP主", video.getAuthor());
        payload.put("分区", board.partition().isBlank() ? board.label() : board.partition());
        payload.put("播放量", video.getView());
        payload.put("点赞量", video.getLike());
        payload.put("投币量", video.getCoin());
        payload.put("收藏量", video.getFavorite());
        payload.put("评论量", video.getReply());
        payload.put("转发量", video.getShare());
        payload.put("时长", video.getDuration());
        payload.put("点赞率", video.getLikeRate());
        payload.put("完播率", video.getCompletionRate());
        payload.put("关键词", topicDataService.extractKeywords(video.getTitle()));
        payload.put("链接", video.getUrl());
        payload.put("BVID", video.getBvid());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception exception) {
            return payload.toString();
        }
    }

    private void notify(Consumer<Map<String, Object>> progressCallback, Map<String, Object> payload) {
        if (progressCallback == null) {
            return;
        }
        try {
            progressCallback.accept(payload);
        } catch (Exception ignored) {
        }
    }

    private int progressPercent(int processedItems, int totalItems, int processedBoards, int totalBoards) {
        if (totalItems > 0) {
            return (int) Math.max(0, Math.min(100, Math.round((processedItems * 100.0) / totalItems)));
        }
        if (totalBoards > 0) {
            return (int) Math.max(0, Math.min(100, Math.round((processedBoards * 100.0) / totalBoards)));
        }
        return 0;
    }

    private String suffix(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index).toLowerCase();
    }

    private record BoardBatch(BoardSpec board, List<VideoMetrics> videos) {
    }

    private record BoardSpec(String label, String boardType, String partition) {
    }
}
