package com.agent4j.bilibili.controller;

import com.agent4j.bilibili.service.LlmClientService;
import com.agent4j.bilibili.service.RuntimeInfoService;
import com.agent4j.bilibili.service.WorkspaceService;
import com.agent4j.bilibili.web.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final WorkspaceService workspaceService;
    private final LlmClientService llmClientService;
    private final RuntimeInfoService runtimeInfoService;

    /**
     * 创建 API 控制器并注入依赖
     * @param workspaceService 工作台服务
     * @param llmClientService LLM 客户端服务
     * @param runtimeInfoService 运行时信息服务
     */
    public ApiController(
            WorkspaceService workspaceService,
            LlmClientService llmClientService,
            RuntimeInfoService runtimeInfoService
    ) {
        this.workspaceService = workspaceService;
        this.llmClientService = llmClientService;
        this.runtimeInfoService = runtimeInfoService;
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
}
