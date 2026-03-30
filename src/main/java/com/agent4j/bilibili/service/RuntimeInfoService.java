package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuntimeInfoService {

    private final AppProperties properties;

    /**
     * 创建运行时信息服务。
     *
     * @param properties 系统配置
     */
    public RuntimeInfoService(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * 构建前端运行时状态信息。
     * 包含运行模式、LLM 能力开关和界面提示文案。
     *
     * @return 供前端展示的运行时数据
     */
    public Map<String, Object> buildRuntimePayload() {
        boolean llmEnabled = properties.llmEnabled();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", properties.runtimeMode());
        payload.put("mode_label", llmEnabled ? "LLM Agent 模式" : "无 Key 规则模式");
        payload.put("llm_enabled", llmEnabled);
        payload.put("chat_available", llmEnabled);
        payload.put("mode_title", llmEnabled ? "当前运行中：LLM Agent 模式" : "当前运行中：无 Key 逻辑模式");
        payload.put("mode_description",
                llmEnabled
                        ? "已切换到 LLM Agent 中枢，分析、决策和生成全部由大模型实时完成。"
                        : "当前未配置 LLM_API_KEY，系统运行在纯代码规则模式，不会消耗 token。");
        payload.put("token_policy", llmEnabled ? "会消耗 token，聊天助手已启用。" : "不会消耗 token，聊天助手当前关闭。");
        payload.put("switch_hint",
                llmEnabled
                        ? "如果要切回逻辑模式，清空 .env 里的 LLM_API_KEY 后重启服务。"
                        : "如果要切到 LLM 模式，填写 .env 里的 LLM_API_KEY、LLM_BASE_URL、LLM_MODEL 后重启服务。");
        return payload;
    }
}
