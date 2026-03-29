package com.agent4j.bilibili.controller;

import com.agent4j.bilibili.service.LlmClientService;
import com.agent4j.bilibili.service.WorkspaceService;
import com.agent4j.bilibili.web.ApiResponse;
import java.util.LinkedHashMap;
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

    public ApiController(WorkspaceService workspaceService, LlmClientService llmClientService) {
        this.workspaceService = workspaceService;
        this.llmClientService = llmClientService;
    }

    @GetMapping("/runtime-info")
    public ApiResponse<Map<String, Object>> runtimeInfo() {
        return ApiResponse.success(workspaceService.runtimeInfo());
    }

    @PostMapping("/resolve-bili-link")
    public ApiResponse<Map<String, Object>> resolveBiliLink(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        if (url.isBlank()) {
            throw new IllegalArgumentException("请先输入 B 站视频链接");
        }
        return ApiResponse.success(workspaceService.resolveBiliLink(url));
    }

    @PostMapping("/module-create")
    public ApiResponse<Map<String, Object>> moduleCreate(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.moduleCreate(body));
    }

    @PostMapping("/module-analyze")
    public ApiResponse<Map<String, Object>> moduleAnalyze(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        if (url.isBlank()) {
            throw new IllegalArgumentException("请先输入 B 站视频链接");
        }
        return ApiResponse.success(workspaceService.moduleAnalyze(body));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", "")).trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("请输入对话内容");
        }
        return ApiResponse.success(workspaceService.chat(body));
    }

    @PostMapping("/topic")
    public ApiResponse<Map<String, Object>> topic(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runTopic(
                String.valueOf(body.getOrDefault("partition", "knowledge")),
                readIntegerList(body.get("up_ids")),
                String.valueOf(body.getOrDefault("topic", ""))
        ));
    }

    @PostMapping("/copy")
    public ApiResponse<Object> copy(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runCopy(
                String.valueOf(body.getOrDefault("topic", "B站内容提纲")),
                String.valueOf(body.getOrDefault("style", "干货"))
        ));
    }

    @PostMapping("/operate")
    public ApiResponse<Object> operate(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runOperate(
                String.valueOf(body.getOrDefault("bv_id", "BV1Demo411111")),
                Boolean.TRUE.equals(body.getOrDefault("dry_run", Boolean.TRUE))
        ));
    }

    @PostMapping("/optimize")
    public ApiResponse<Object> optimize(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(workspaceService.runOptimize(
                String.valueOf(body.getOrDefault("bv_id", "BV1Demo411111"))
        ));
    }

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

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handle(Exception exception) {
        int status = llmClientService.llmErrorHttpStatus(exception);
        String message = exception.getMessage() == null ? "服务异常" : exception.getMessage();
        if (status == 500 && (message.contains("LLM") || message.contains("api key") || message.contains("quota"))) {
            message = llmClientService.formatLlmError(exception);
        }
        return ResponseEntity.status(status == 500 ? HttpStatus.BAD_REQUEST : HttpStatus.valueOf(status))
                .body(ApiResponse.failure(message));
    }

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
}
