package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LlmClientService {

    private final AppProperties properties;
    private final RuntimeLlmConfigService runtimeLlmConfigService;
    private final ObjectMapper objectMapper;

    /**
     * 创建 LLM 客户端服务。
     *
     * @param properties 系统配置
     * @param objectMapper JSON 映射器
     */
    public LlmClientService(
            AppProperties properties,
            RuntimeLlmConfigService runtimeLlmConfigService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.runtimeLlmConfigService = runtimeLlmConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断当前是否可使用 LLM。
     *
     * @return 是否已启用 LLM
     */
    public boolean available() {
        return runtimeLlmConfigService.runtimeLlmEnabled();
    }

    /**
     * 校验当前环境是否允许调用 LLM。
     */
    public void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("LLM unavailable: configure runtime LLM settings first.");
        }
    }

    /**
     * 调用 LLM 生成 JSON，并在失败时返回回退值。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param fallback 回退结果
     * @return JSON 节点结果
     */
    public JsonNode invokeJson(String systemPrompt, String userPrompt, Object fallback) {
        if (!available()) {
            return objectMapper.valueToTree(fallback);
        }
        try {
            return invokeJsonRequired(systemPrompt, userPrompt);
        } catch (Exception exception) {
            return objectMapper.valueToTree(fallback);
        }
    }

    /**
     * 调用 LLM 并强制解析为 JSON。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return JSON 节点结果
     */
    public JsonNode invokeJsonRequired(String systemPrompt, String userPrompt) {
        requireAvailable();
        String text = invokeTextRequired(
                systemPrompt,
                userPrompt + "\n\nReturn JSON only. Do not add explanation outside the JSON payload."
        );
        return JsonUtils.readTree(objectMapper, JsonUtils.extractJsonBlock(text));
    }

    /**
     * 调用 LLM 生成文本，并在失败时返回回退值。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param fallback 回退文本
     * @return 模型返回文本
     */
    public String invokeText(String systemPrompt, String userPrompt, String fallback) {
        if (!available()) {
            return fallback;
        }
        try {
            return invokeTextRequired(systemPrompt, userPrompt);
        } catch (Exception exception) {
            return fallback;
        }
    }

    /**
     * 调用 LLM 生成文本，并按配置执行重试。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型返回文本
     */
    public String invokeTextRequired(String systemPrompt, String userPrompt) {
        requireAvailable();
        RuntimeLlmConfigService.RuntimeLlmConfig config = runtimeLlmConfigService.getActiveRuntimeLlmConfig();
        if (config == null) {
            throw new IllegalStateException("LLM unavailable: runtime configuration is missing.");
        }
        int attempts = Math.max(1, properties.getLlmMaxRetries() + 1);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                List<ChatMessage> messages = List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                );
                ChatResponse response = chatModel(config).chat(
                        ChatRequest.builder()
                                .messages(messages)
                                .temperature(0.7)
                                .build()
                );
                if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
                    throw new IllegalStateException("LLM returned empty content");
                }
                return response.aiMessage().text();
            } catch (RuntimeException exception) {
                lastError = exception;
                if (attempt >= attempts || !isRetryableLlmError(exception)) {
                    throw exception;
                }
                sleepRetry(attempt);
            }
        }
        throw lastError == null ? new IllegalStateException("LLM invocation failed") : lastError;
    }

    /**
     * 将底层 LLM 异常转换为可读错误说明。
     *
     * @param throwable 异常对象
     * @return 错误描述
     */
    public String formatLlmError(Throwable throwable) {
        String category = classifyLlmError(throwable);
        return switch (category) {
            case "billing_service_unavailable" -> "上游 LLM 服务暂时不可用（503 / billing_service_error）。";
            case "service_unavailable" -> "上游 LLM 服务暂时不可用（503）。";
            case "bad_gateway", "gateway_timeout" -> "上游 LLM 网关暂时异常（502/504）。";
            case "timeout" -> "LLM 请求超时，请稍后重试。";
            case "rate_limit" -> "上游 LLM 当前限流，请稍后重试。";
            case "auth" -> "LLM API Key 无效或鉴权失败，请检查当前运行时配置。";
            case "quota" -> "LLM 服务当前不可用，可能是额度或权限限制。";
            default -> throwable == null ? "未知 LLM 错误" : throwable.getMessage();
        };
    }

    /**
     * 根据异常类型推断 HTTP 状态码。
     *
     * @param throwable 异常对象
     * @return 建议返回的状态码
     */
    public int llmErrorHttpStatus(Throwable throwable) {
        return isRetryableLlmError(throwable) ? 503 : 500;
    }

    /**
     * 判断是否应跳过同供应商回退。
     *
     * @param throwable 异常对象
     * @return 是否跳过同供应商重试
     */
    public boolean shouldSkipSameProviderFallback(Throwable throwable) {
        String category = classifyLlmError(throwable);
        return "billing_service_unavailable".equals(category)
                || "service_unavailable".equals(category)
                || "bad_gateway".equals(category)
                || "gateway_timeout".equals(category)
                || "auth".equals(category)
                || "quota".equals(category);
    }

    /**
     * 判断当前异常是否应该引导前端拉起运行时重配表单。
     *
     * @param throwable 异常对象
     * @return 是否建议重配
     */
    public boolean shouldPromptRuntimeConfig(Throwable throwable) {
        String category = classifyLlmError(throwable);
        if (!"unknown".equals(category)) {
            return true;
        }
        String text = throwable == null ? "" : String.valueOf(throwable.getMessage()).toLowerCase();
        return text.contains("llm")
                || text.contains("api key")
                || text.contains("runtime configuration")
                || text.contains("provider")
                || text.contains("quota");
    }

    /**
     * 基于当前配置创建聊天模型。
     *
     * @return LangChain4j 聊天模型
     */
    private ChatModel chatModel(RuntimeLlmConfigService.RuntimeLlmConfig config) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(properties.getLlmTimeoutSeconds()))
                .maxRetries(0)
                .store(!properties.isLlmDisableResponseStorage());
        String reasoningEffort = String.valueOf(properties.getLlmReasoningEffort()).trim();
        if (!reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }
        return builder.build();
    }

    /**
     * 识别 LLM 异常类别。
     *
     * @param throwable 异常对象
     * @return 错误类别标识
     */
    private String classifyLlmError(Throwable throwable) {
        String text = throwable == null ? "" : String.valueOf(throwable.getMessage()).toLowerCase();
        if (text.contains("billing_service_error") || text.contains("billing service temporarily unavailable")) {
            return "billing_service_unavailable";
        }
        if (text.contains("503") && text.contains("unavailable")) {
            return "service_unavailable";
        }
        if (text.contains("502") || text.contains("bad gateway")) {
            return "bad_gateway";
        }
        if (text.contains("504") || text.contains("gateway timeout")) {
            return "gateway_timeout";
        }
        if (text.contains("timeout")) {
            return "timeout";
        }
        if (text.contains("429") || text.contains("rate limit") || text.contains("too many requests")) {
            return "rate_limit";
        }
        if (text.contains("401") || text.contains("invalid api key") || text.contains("authentication")) {
            return "auth";
        }
        if (text.contains("403") || text.contains("quota") || text.contains("insufficient")) {
            return "quota";
        }
        return "unknown";
    }

    /**
     * 判断异常是否属于可重试的 LLM 错误。
     *
     * @param throwable 异常对象
     * @return 是否可重试
     */
    private boolean isRetryableLlmError(Throwable throwable) {
        String category = classifyLlmError(throwable);
        return "billing_service_unavailable".equals(category)
                || "service_unavailable".equals(category)
                || "bad_gateway".equals(category)
                || "gateway_timeout".equals(category)
                || "timeout".equals(category)
                || "rate_limit".equals(category);
    }

    /**
     * 按退避策略暂停后继续重试。
     *
     * @param attempt 当前重试次数
     */
    private void sleepRetry(int attempt) {
        try {
            Thread.sleep((long) (properties.getLlmRetryBackoffSeconds() * attempt * 1000));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 通过 PowerShell 调用 HTTP API。
     * 当 Python sockets 在某些 Windows 环境被阻止时使用此备选方案。
     *
     * @param endpoint API 端点
     * @param payload 请求体
     * @return 响应文本
     */
    private String invokeViaPowerShellHttp(String endpoint, Map<String, Object> payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            // PowerShell 脚本：使用 Invoke-RestMethod 调用 API
            String psScript = String.format(
                    "$headers = @{'Content-Type'='application/json'}; "
                            + "$body = '%s' | ConvertTo-Json -Compress; "
                            + "$response = Invoke-RestMethod -Uri '%s' -Method Post -Headers $headers -Body $body -TimeoutSec 60; "
                            + "$response | ConvertTo-Json -Compress",
                    jsonPayload.replace("'", "''"),
                    endpoint
            );

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("PowerShell HTTP fallback failed with exit code: " + exitCode);
            }

            return output.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException("PowerShell HTTP fallback failed: " + e.getMessage(), e);
        }
    }

    /**
     * 判断是否应该使用 PowerShell fallback。
     * 当检测到 Windows socket 问题时返回 true。
     */
    private boolean shouldUsePowerShellFallback(Exception exception) {
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        return message.contains("connection refused")
                || message.contains("connect timed out")
                || message.contains("network is unreachable")
                || message.contains("permission denied");
    }
}
