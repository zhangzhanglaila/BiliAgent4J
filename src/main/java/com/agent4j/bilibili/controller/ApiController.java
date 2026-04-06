package com.agent4j.bilibili.controller;

import com.agent4j.bilibili.service.ChatSessionService;
import com.agent4j.bilibili.service.KnowledgeBaseService;
import com.agent4j.bilibili.service.KnowledgeSyncService;
import com.agent4j.bilibili.service.KnowledgeUpdateJobService;
import com.agent4j.bilibili.service.LongTermMemoryService;
import com.agent4j.bilibili.service.LlmClientService;
import com.agent4j.bilibili.service.RuntimeInfoService;
import com.agent4j.bilibili.service.SseEmitterService;
import com.agent4j.bilibili.service.WorkspaceService;
import com.agent4j.bilibili.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final WorkspaceService workspaceService;
    private final LlmClientService llmClientService;
    private final RuntimeInfoService runtimeInfoService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeSyncService knowledgeSyncService;
    private final LongTermMemoryService longTermMemoryService;
    private final KnowledgeUpdateJobService knowledgeUpdateJobService;
    private final ChatSessionService chatSessionService;
    private final SseEmitterService sseEmitterService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-task");
        t.setDaemon(true);
        return t;
    });

    /**
     * 创建 API 控制器并注入依赖
     * @param workspaceService 工作台服务
     * @param llmClientService LLM 客户端服务
     * @param runtimeInfoService 运行时信息服务
     */
    public ApiController(
            WorkspaceService workspaceService,
            LlmClientService llmClientService,
            RuntimeInfoService runtimeInfoService,
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeSyncService knowledgeSyncService,
            LongTermMemoryService longTermMemoryService,
            KnowledgeUpdateJobService knowledgeUpdateJobService,
            ChatSessionService chatSessionService,
            SseEmitterService sseEmitterService
    ) {
        this.workspaceService = workspaceService;
        this.llmClientService = llmClientService;
        this.runtimeInfoService = runtimeInfoService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeSyncService = knowledgeSyncService;
        this.longTermMemoryService = longTermMemoryService;
        this.knowledgeUpdateJobService = knowledgeUpdateJobService;
        this.chatSessionService = chatSessionService;
        this.sseEmitterService = sseEmitterService;
    }

    /**
     * 获取运行时信息接口
     * @return 标准接口响应
     */
    @GetMapping("/runtime-info")
    public ApiResponse<Map<String, Object>> runtimeInfo() {
        return ApiResponse.success(workspaceService.runtimeInfo());
    }

    /**
     * 切换运行时模式开关。
     *
     * @param body 请求体
     * @return 最新运行时信息
     */
    @PostMapping("/runtime-mode")
    public ApiResponse<Map<String, Object>> runtimeMode(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.setRuntimeMode(readBoolean(body.get("enabled"))));
    }

    /**
     * 保存运行时 LLM 配置。
     *
     * @param body 请求体
     * @return 最新运行时信息
     */
    @PostMapping("/runtime-llm-config")
    public ApiResponse<Map<String, Object>> runtimeLlmConfig(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.saveRuntimeLlmConfig(body));
    }

    @GetMapping("/knowledge/status")
    public ApiResponse<Map<String, Object>> knowledgeStatus() {
        return ApiResponse.success(
                knowledgeSyncService.buildKnowledgeBaseStatus(
                        longTermMemoryService,
                        knowledgeUpdateJobService.getActiveKnowledgeUpdateJob()
                )
        );
    }

    @GetMapping("/knowledge/sample")
    public ApiResponse<Map<String, Object>> knowledgeSample(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        return ApiResponse.success(knowledgeBaseService.sample(limit, offset, null));
    }

    @GetMapping("/knowledge/search")
    public ApiResponse<Map<String, Object>> knowledgeSearch(
            @RequestParam(name = "q") String query,
            @RequestParam(name = "limit", defaultValue = "6") int limit
    ) {
        String cleanQuery = String.valueOf(query).trim();
        if (cleanQuery.isBlank()) {
            throw new IllegalArgumentException("请输入检索关键词。");
        }
        Map<String, Object> raw = knowledgeBaseService.retrieve(cleanQuery, Math.max(limit, 24), null);
        List<Map<String, Object>> matches = raw.get("matches") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> castMap((Map<?, ?>) item)).toList()
                : List.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", cleanQuery);
        payload.put("matches", knowledgeSyncService.collapseKnowledgeMatches(matches).stream().limit(Math.max(1, limit)).toList());
        return ApiResponse.success(payload);
    }

    @PostMapping(value = "/knowledge/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> knowledgeUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择要上传的知识文件。");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("upload_result", knowledgeSyncService.ingestUploadedFile(
                file.getOriginalFilename(),
                file.getBytes(),
                Map.of("source_channel", "web_upload")
        ));
        payload.put("knowledge_status", knowledgeSyncService.buildKnowledgeBaseStatus(
                longTermMemoryService,
                knowledgeUpdateJobService.getActiveKnowledgeUpdateJob()
        ));
        return ApiResponse.success(payload);
    }

    @PostMapping("/knowledge/update")
    public ApiResponse<Map<String, Object>> knowledgeUpdate(@RequestBody(required = false) Map<String, Object> body) {
        int limit = readInt(body == null ? null : body.get("limit"), 10, 1, 20);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("update_result", knowledgeSyncService.updateKnowledgeBase(limit, null));
        payload.put("knowledge_status", knowledgeSyncService.buildKnowledgeBaseStatus(
                longTermMemoryService,
                knowledgeUpdateJobService.getActiveKnowledgeUpdateJob()
        ));
        return ApiResponse.success(payload);
    }

    @PostMapping("/knowledge/update/start")
    public ApiResponse<Map<String, Object>> knowledgeUpdateStart(@RequestBody(required = false) Map<String, Object> body) {
        int limit = readInt(body == null ? null : body.get("limit"), 10, 1, 20);
        Map<String, Object> result = knowledgeUpdateJobService.startKnowledgeUpdate(limit);
        if (!String.valueOf(result.getOrDefault("error", "")).isBlank()) {
            throw new IllegalStateException(String.valueOf(result.get("error")));
        }
        return ApiResponse.success(Map.of(
                "job", result.get("job"),
                "already_running", result.get("already_running")
        ));
    }

    @GetMapping("/knowledge/update/{jobId}")
    public ApiResponse<Map<String, Object>> knowledgeUpdateJob(@PathVariable("jobId") String jobId) {
        Map<String, Object> job = knowledgeUpdateJobService.getKnowledgeUpdateJob(String.valueOf(jobId).trim());
        if (job == null) {
            throw new IllegalArgumentException("未找到对应的知识库更新任务。");
        }
        return ApiResponse.success(job);
    }

    /**
     * 接收视频链接解析请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/resolve-bili-link")
    public ApiResponse<Map<String, Object>> resolveBiliLink(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        if (url.isBlank()) {
            throw new IllegalArgumentException("请先输入 B 站视频链接");
        }
        return ApiResponse.success(workspaceService.resolveBiliLink(url));
    }

    /**
     * 接收创作模块生成请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/module-create")
    public ApiResponse<Map<String, Object>> moduleCreate(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.moduleCreate(body));
    }

    /**
     * 接收视频分析模块请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/module-analyze")
    public ApiResponse<Map<String, Object>> moduleAnalyze(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        if (url.isBlank()) {
            throw new IllegalArgumentException("请先输入 B 站视频链接");
        }
        return ApiResponse.success(workspaceService.moduleAnalyze(body));
    }

    /**
     * 接收智能助手对话请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", "")).trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("请输入对话内容");
        }
        return ApiResponse.success(workspaceService.chat(body));
    }

    /**
     * 接收选题生成请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/topic")
    public ApiResponse<Map<String, Object>> topic(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runTopic(
                String.valueOf(body.getOrDefault("partition", "knowledge")),
                readIntegerList(body.get("up_ids")),
                String.valueOf(body.getOrDefault("topic", ""))
        ));
    }

    /**
     * 接收文案生成请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/copy")
    public ApiResponse<Object> copy(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runCopy(
                String.valueOf(body.getOrDefault("topic", "B站内容提纲")),
                String.valueOf(body.getOrDefault("style", "干货"))
        ));
    }

    /**
     * 接收互动运营请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/operate")
    public ApiResponse<Object> operate(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runOperate(
                String.valueOf(body.getOrDefault("bv_id", "BV1Demo411111")),
                Boolean.TRUE.equals(body.getOrDefault("dry_run", Boolean.TRUE))
        ));
    }

    /**
     * 接收视频优化请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/optimize")
    public ApiResponse<Object> optimize(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runOptimize(
                String.valueOf(body.getOrDefault("bv_id", "BV1Demo411111"))
        ));
    }

    /**
     * 接收整套流水线执行请求
     * @param body 请求体数据
     * @return 标准接口响应
     */
    @PostMapping("/pipeline")
    public ApiResponse<Map<String, Object>> pipeline(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runPipeline(
                String.valueOf(body.getOrDefault("bv_id", "BV1Demo411111")),
                String.valueOf(body.getOrDefault("partition", "knowledge")),
                readIntegerList(body.get("up_ids")),
                String.valueOf(body.getOrDefault("style", "干货")),
                String.valueOf(body.getOrDefault("topic", ""))
        ));
    }

    // ==================== Chat Session 端点 ====================

    /**
     * 获取所有聊天会话列表。
     */
    @GetMapping("/chat/sessions")
    public ApiResponse<List<Map<String, Object>>> listChatSessions() {
        return ApiResponse.success(chatSessionService.listSessions());
    }

    /**
     * 获取指定会话的详情。
     */
    @GetMapping("/chat/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> getChatSession(@PathVariable("sessionId") String sessionId) {
        Map<String, Object> session = chatSessionService.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("未找到对应的会话。");
        }
        return ApiResponse.success(session);
    }

    /**
     * 删除指定会话。
     */
    @PostMapping("/chat/sessions/{sessionId}/delete")
    public ApiResponse<Map<String, Object>> deleteChatSession(@PathVariable("sessionId") String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return ApiResponse.success(Map.of("deleted", true, "session_id", sessionId));
    }

    /**
     * 保存聊天会话。
     */
    @PostMapping("/chat/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> saveChatSession(
            @PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, Object> body
    ) {
        String firstQuestion = String.valueOf(body.getOrDefault("first_question", ""));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        int historyLimit = readInt(body.get("history_limit"), 50, 1, 200);
        List<Map<String, String>> normalizedHistory = chatSessionService.normalizeHistory(history, historyLimit);
        chatSessionService.saveSession(sessionId, firstQuestion, normalizedHistory);
        return ApiResponse.success(Map.of("saved", true, "session_id", sessionId));
    }

    /**
     * 获取 SSE 流式分析任务状态。
     */
    @GetMapping("/module-analyze/jobs/{jobId}/events")
    public SseEmitter sseModuleAnalyzeJobEvents(@PathVariable("jobId") String jobId) {
        SseEmitter emitter = sseEmitterService.createEmitter(jobId);
        return emitter;
    }

    /**
     * 启动异步视频分析任务。
     */
    @PostMapping("/module-analyze/start")
    public ApiResponse<Map<String, Object>> moduleAnalyzeStart(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        if (url.isBlank()) {
            throw new IllegalArgumentException("请先输入 B 站视频链接");
        }
        String jobId = chatSessionService.generateSessionId();

        // 异步执行分析
        sseExecutor.execute(() -> {
            try {
                Map<String, Object> result = workspaceService.moduleAnalyze(body);
                sseEmitterService.sendComplete(jobId, result);
            } catch (Exception e) {
                sseEmitterService.sendError(jobId, e.getMessage());
            }
        });

        return ApiResponse.success(Map.of("job_id", jobId, "status", "started"));
    }

    /**
     * 获取异步分析任务状态。
     */
    @GetMapping("/module-analyze/jobs/{jobId}")
    public ApiResponse<Map<String, Object>> moduleAnalyzeJob(@PathVariable("jobId") String jobId) {
        if (!sseEmitterService.hasActiveEmitter(jobId)) {
            return ApiResponse.success(Map.of("job_id", jobId, "status", "not_found"));
        }
        return ApiResponse.success(Map.of("job_id", jobId, "status", "running"));
    }

    /**
     * 统一处理控制器异常并包装错误响应
     * @param exception 捕获到的异常
     * @return 标准错误响应
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handle(Exception exception) {
        int status = llmClientService.llmErrorHttpStatus(exception);
        String message = exception.getMessage() == null ? "服务异常" : exception.getMessage();
        if (status == 500 && (message.contains("LLM") || message.contains("api key") || message.contains("quota"))) {
            message = llmClientService.formatLlmError(exception);
        }
        Map<String, Object> data = llmClientService.shouldPromptRuntimeConfig(exception)
                ? runtimeInfoService.buildLlmRuntimeReconfigureData(message)
                : null;
        return ResponseEntity.status(status == 500 ? HttpStatus.BAD_REQUEST : HttpStatus.valueOf(status))
                .body(data == null ? ApiResponse.failure(message) : ApiResponse.failure(message, data));
    }

    /**
     * 读取请求中的整数 ID 列表
     * @param raw 原始参数
     * @return 过滤后的正整数列表
     */
    private List<Integer> readIntegerList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> {
            try {
                return Integer.parseInt(String.valueOf(item));
            } catch (Exception exception) {
                return 0;
            }
        }).filter(value -> value > 0).toList();
    }

    /**
     * 读取布尔值参数。
     *
     * @param raw 原始值
     * @return 布尔结果
     */
    private boolean readBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    private int readInt(Object raw, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(String.valueOf(raw == null ? fallback : raw).trim());
            return Math.max(min, Math.min(value, max));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
