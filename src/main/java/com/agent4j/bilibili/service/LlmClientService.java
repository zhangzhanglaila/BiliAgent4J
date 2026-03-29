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
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LlmClientService {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private ChatModel chatModel;

    public LlmClientService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean available() {
        return properties.llmEnabled();
    }

    public void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("LLM unavailable: configure LLM_API_KEY first.");
        }
    }

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

    public JsonNode invokeJsonRequired(String systemPrompt, String userPrompt) {
        requireAvailable();
        String text = invokeTextRequired(
                systemPrompt,
                userPrompt + "\n\nReturn JSON only. Do not add explanation outside the JSON payload."
        );
        return JsonUtils.readTree(objectMapper, JsonUtils.extractJsonBlock(text));
    }

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

    public String invokeTextRequired(String systemPrompt, String userPrompt) {
        requireAvailable();
        int attempts = Math.max(1, properties.getLlmMaxRetries() + 1);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                List<ChatMessage> messages = List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                );
                ChatResponse response = chatModel().chat(
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

    public String formatLlmError(Throwable throwable) {
        String category = classifyLlmError(throwable);
        return switch (category) {
            case "billing_service_unavailable" -> "上游 LLM 服务暂时不可用（503 / billing_service_error）。";
            case "service_unavailable" -> "上游 LLM 服务暂时不可用（503）。";
            case "bad_gateway", "gateway_timeout" -> "上游 LLM 网关暂时异常（502/504）。";
            case "timeout" -> "LLM 请求超时，请稍后重试。";
            case "rate_limit" -> "上游 LLM 当前限流，请稍后重试。";
            case "auth" -> "LLM API Key 无效或鉴权失败，请检查 .env 配置。";
            case "quota" -> "LLM 服务当前不可用，可能是额度或权限限制。";
            default -> throwable == null ? "未知 LLM 错误" : throwable.getMessage();
        };
    }

    public int llmErrorHttpStatus(Throwable throwable) {
        return isRetryableLlmError(throwable) ? 503 : 500;
    }

    public boolean shouldSkipSameProviderFallback(Throwable throwable) {
        String category = classifyLlmError(throwable);
        return "billing_service_unavailable".equals(category)
                || "service_unavailable".equals(category)
                || "bad_gateway".equals(category)
                || "gateway_timeout".equals(category)
                || "auth".equals(category)
                || "quota".equals(category);
    }

    private ChatModel chatModel() {
        if (chatModel == null) {
            // Build the LangChain4j model lazily so rules mode can run without any LLM config.
            chatModel = OpenAiChatModel.builder()
                    .apiKey(properties.getLlmApiKey())
                    .baseUrl(properties.getLlmBaseUrl())
                    .modelName(properties.getLlmModel())
                    .timeout(Duration.ofSeconds(properties.getLlmTimeoutSeconds()))
                    .maxRetries(0)
                    .build();
        }
        return chatModel;
    }

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

    private boolean isRetryableLlmError(Throwable throwable) {
        String category = classifyLlmError(throwable);
        return "billing_service_unavailable".equals(category)
                || "service_unavailable".equals(category)
                || "bad_gateway".equals(category)
                || "gateway_timeout".equals(category)
                || "timeout".equals(category)
                || "rate_limit".equals(category);
    }

    private void sleepRetry(int attempt) {
        try {
            Thread.sleep((long) (properties.getLlmRetryBackoffSeconds() * attempt * 1000));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
