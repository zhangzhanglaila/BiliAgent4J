package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuntimeLlmConfigService {

    private final AppProperties properties;
    private RuntimeLlmConfig savedConfig;
    private boolean runtimeEnabled;

    /**
     * 初始化运行时 LLM 配置状态。
     * 如果 .env 已经提供了可用配置，启动时默认视为已保存并启用。
     *
     * @param properties 应用基础配置
     */
    public RuntimeLlmConfigService(AppProperties properties) {
        this.properties = properties;
        if (properties.getLlmApiKey() != null && !properties.getLlmApiKey().isBlank()) {
            this.savedConfig = new RuntimeLlmConfig(
                    defaultProvider(),
                    normalizeBaseUrl(properties.getLlmBaseUrl()),
                    properties.getLlmApiKey().trim(),
                    defaultModel(),
                    "env"
            );
            this.runtimeEnabled = true;
        } else {
            this.savedConfig = null;
            this.runtimeEnabled = false;
        }
    }

    /**
     * 判断当前是否已经保存过一组可复用的 LLM 配置。
     *
     * @return 是否存在可用配置
     */
    public synchronized boolean hasSavedRuntimeLlmConfig() {
        return savedConfig != null && savedConfig.hasApiKey();
    }

    /**
     * 判断当前开关状态下是否真正启用了 LLM Agent。
     *
     * @return 是否处于启用状态
     */
    public synchronized boolean runtimeLlmEnabled() {
        return runtimeEnabled && hasSavedRuntimeLlmConfig();
    }

    /**
     * 返回当前前端模式开关是否处于打开状态。
     *
     * @return 是否已打开
     */
    public synchronized boolean switchChecked() {
        return runtimeEnabled;
    }

    /**
     * 返回当前保存的运行时配置。
     *
     * @return 配置对象，没有则返回 null
     */
    public synchronized RuntimeLlmConfig getSavedRuntimeLlmConfig() {
        return savedConfig == null ? null : savedConfig.copy();
    }

    /**
     * 返回当前真正生效中的运行时配置。
     *
     * @return 激活配置，没有则返回 null
     */
    public synchronized RuntimeLlmConfig getActiveRuntimeLlmConfig() {
        return runtimeLlmEnabled() ? savedConfig.copy() : null;
    }

    /**
     * 开关运行时 LLM 模式，但保留已保存的配置。
     *
     * @param enabled 是否启用
     */
    public synchronized void setRuntimeLlmEnabled(boolean enabled) {
        this.runtimeEnabled = enabled;
    }

    /**
     * 保存页面提交的 LLM 配置，并立即切换到启用状态。
     *
     * @param payload 前端请求体
     * @return 保存后的配置副本
     */
    public synchronized RuntimeLlmConfig saveRuntimeLlmConfig(Map<String, Object> payload) {
        String baseUrl = normalizeBaseUrl(stringValue(payload.get("base_url")));
        String apiKey = stringValue(payload.get("api_key"));
        String provider = stringValue(payload.get("provider"));
        String model = stringValue(payload.get("model"));

        if (baseUrl.isBlank() || apiKey.isBlank() || provider.isBlank()) {
            throw new IllegalArgumentException("请完整填写 URL、Key 和模型供应商。");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("URL 需要以 http:// 或 https:// 开头。");
        }

        this.savedConfig = new RuntimeLlmConfig(
                provider,
                baseUrl,
                apiKey,
                model.isBlank() ? defaultModel() : model,
                "runtime"
        );
        this.runtimeEnabled = true;
        return this.savedConfig.copy();
    }

    /**
     * 返回当前运行模式名称。
     *
     * @return `llm_agent` 或 `rules`
     */
    public synchronized String runtimeMode() {
        return runtimeLlmEnabled() ? "llm_agent" : "rules";
    }

    /**
     * 读取默认 provider 名称。
     *
     * @return provider 名称
     */
    public String defaultProvider() {
        String provider = properties.getLlmProvider();
        return provider == null || provider.isBlank() ? "openai" : provider.trim();
    }

    /**
     * 读取默认模型名称。
     *
     * @return 模型名称
     */
    public String defaultModel() {
        String model = properties.getLlmModel();
        return model == null || model.isBlank() ? "gpt-5.4" : model.trim();
    }

    /**
     * 对 API Key 做脱敏处理，避免完整返回到前端。
     *
     * @param value 原始密钥
     * @return 脱敏后的密钥
     */
    public String maskApiKey(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.length() <= 8) {
            return "*".repeat(raw.length());
        }
        return raw.substring(0, 4) + "*".repeat(Math.max(4, raw.length() - 8)) + raw.substring(raw.length() - 4);
    }

    /**
     * 将配置对象转换为普通 Map，便于调试或接口透传。
     *
     * @param config 配置对象
     * @return 普通 Map
     */
    public Map<String, Object> toMap(RuntimeLlmConfig config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (config == null) {
            return payload;
        }
        payload.put("provider", config.provider());
        payload.put("base_url", config.baseUrl());
        payload.put("api_key", config.apiKey());
        payload.put("model", config.model());
        payload.put("source", config.source());
        return payload;
    }

    /**
     * 读取字符串值并去除空白。
     *
     * @param value 原始值
     * @return 字符串结果
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 规范化 baseUrl。
     *
     * @param value 原始地址
     * @return 去掉末尾斜杠后的地址
     */
    private String normalizeBaseUrl(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record RuntimeLlmConfig(String provider, String baseUrl, String apiKey, String model, String source) {

        /**
         * 判断当前配置是否包含可用 API Key。
         *
         * @return 是否可用
         */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /**
         * 返回一份防止外部误修改的副本。
         *
         * @return 新副本
         */
        public RuntimeLlmConfig copy() {
            return new RuntimeLlmConfig(provider, baseUrl, apiKey, model, source);
        }
    }
}
