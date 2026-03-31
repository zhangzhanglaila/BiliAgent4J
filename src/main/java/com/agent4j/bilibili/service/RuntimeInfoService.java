package com.agent4j.bilibili.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuntimeInfoService {

    private final RuntimeLlmConfigService runtimeLlmConfigService;

    /**
     * 创建运行时信息服务。
     *
     * @param runtimeLlmConfigService 运行时 LLM 配置服务
     */
    public RuntimeInfoService(RuntimeLlmConfigService runtimeLlmConfigService) {
        this.runtimeLlmConfigService = runtimeLlmConfigService;
    }

    /**
     * 构建前端运行时状态信息。
     * 包含运行模式、LLM 能力开关和界面提示文案。
     *
     * @return 供前端展示的运行时数据
     */
    public Map<String, Object> buildRuntimePayload() {
        boolean llmEnabled = runtimeLlmConfigService.runtimeLlmEnabled();
        boolean hasSavedConfig = runtimeLlmConfigService.hasSavedRuntimeLlmConfig();
        RuntimeLlmConfigService.RuntimeLlmConfig savedConfig = runtimeLlmConfigService.getSavedRuntimeLlmConfig();
        String configSource = savedConfig == null ? "" : savedConfig.source();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", runtimeLlmConfigService.runtimeMode());
        payload.put("mode_label", llmEnabled ? "LLM Agent 模式" : "无 Key 逻辑模式");
        payload.put("llm_enabled", llmEnabled);
        payload.put("chat_available", llmEnabled);
        payload.put("switch_checked", runtimeLlmConfigService.switchChecked());
        payload.put("has_saved_llm_config", hasSavedConfig);
        payload.put("saved_config_source", configSource);
        payload.put("saved_provider", savedConfig == null ? "" : savedConfig.provider());
        payload.put("saved_model", savedConfig == null ? "" : savedConfig.model());
        payload.put("saved_base_url", savedConfig == null ? "" : savedConfig.baseUrl());
        payload.put("saved_api_key_masked", savedConfig == null ? "" : runtimeLlmConfigService.maskApiKey(savedConfig.apiKey()));
        payload.put("requires_config", false);
        payload.put("mode_title", llmEnabled ? "当前运行中：LLM Agent 模式" : "当前运行中：无 Key 逻辑模式");
        payload.put("mode_description", llmEnabled
                ? "已切换到 LLM Agent 中枢，分析、决策和生成全部由大模型实时完成。"
                : "当前运行在无 Key 逻辑模式，分析和生成走规则链路，不会消耗 token。");
        payload.put("token_policy", llmEnabled ? "会消耗 token，聊天助手已启用。" : "不会消耗 token，聊天助手当前关闭。");
        payload.put("switch_hint", llmEnabled
                ? "关闭右侧开关即可立即切回无 Key 逻辑模式。"
                : hasSavedConfig
                ? "当前已经保存可用的 LLM 配置，打开右侧开关即可恢复到 LLM Agent 模式。"
                : "当前还没有可用 LLM 配置，打开右侧开关后需要先填写 URL、Key 和模型供应商。");
        return payload;
    }

    /**
     * 构造前端可直接使用的 LLM 重配提示数据。
     *
     * @param reason 触发重配的原因
     * @return 前端错误附带数据
     */
    public Map<String, Object> buildLlmRuntimeReconfigureData(String reason) {
        Map<String, Object> runtimePayload = new LinkedHashMap<>(buildRuntimePayload());
        runtimePayload.put("requires_config", true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("show_runtime_config", true);
        payload.put("reason", reason);
        payload.put("runtime_payload", runtimePayload);
        return payload;
    }
}
