package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class LlmWorkspaceAgentService {

    private final LlmClientService llmClientService;
    private final ObjectMapper objectMapper;
    private final TopicDataService topicDataService;
    private final VideoResolverService videoResolverService;
    private final AppProperties properties;

    /**
     * 创建 LLM 工作台代理服务。
     *
     * @param llmClientService LLM 客户端服务
     * @param objectMapper JSON 映射器
     * @param topicDataService 选题数据服务
     * @param videoResolverService 视频解析服务
     * @param properties 系统配置
     */
    public LlmWorkspaceAgentService(
            LlmClientService llmClientService,
            ObjectMapper objectMapper,
            TopicDataService topicDataService,
            VideoResolverService videoResolverService,
            AppProperties properties
    ) {
        this.llmClientService = llmClientService;
        this.objectMapper = objectMapper;
        this.topicDataService = topicDataService;
        this.videoResolverService = videoResolverService;
        this.properties = properties;
    }

    /**
     * 运行创作模块，生成选题和文案结果。
     *
     * @param data 用户输入数据
     * @return 模块执行结果
     */
    public Map<String, Object> runModuleCreate(Map<String, Object> data) {
        Map<String, Object> result = runStructured(
                "module_create",
                "基于用户输入和实时市场样本，为创作者输出更容易起量的 3 个选题，并生成完整可发布文案。",
                Map.of(
                        "field", stringValue(data.get("field")),
                        "direction", stringValue(data.get("direction")),
                        "idea", stringValue(data.get("idea")),
                        "partition", stringValue(data.getOrDefault("partition", "knowledge")),
                        "style", stringValue(data.getOrDefault("style", "干货"))
                ),
                """
                        返回一个 JSON 对象，字段必须包含：
                        - normalized_profile: 字符串
                        - seed_topic: 字符串
                        - partition: 字符串
                        - style: 字符串
                        - chosen_topic: 字符串
                        - topic_result: 对象，至少包含 ideas(长度 3 的数组)
                        - copy_result: 对象，包含 topic, style, titles, script, description, tags, pinned_comment
                        """,
                List.of("creator_briefing"),
                List.of("creator_briefing"),
                List.of("normalized_profile", "seed_topic", "partition", "style", "chosen_topic", "topic_result", "copy_result"),
                2
        );
        result.putIfAbsent("runtime_mode", "llm_agent");
        return result;
    }

    /**
     * 运行分析模块，输出视频分析和优化建议。
     *
     * @param data 用户输入数据
     * @param resolved 视频解析结果
     * @param marketSnapshot 市场快照
     * @return 模块执行结果
     */
    public Map<String, Object> runModuleAnalyze(Map<String, Object> data, Map<String, Object> resolved, Map<String, Object> marketSnapshot) {
        Map<String, Object> result = runStructured(
                "module_analyze",
                "基于后端已经解析出的当前视频真实信息，以及同类市场样本，判断它更接近爆款还是低表现，并解释原因，同时给出后续选题和优化方案。",
                Map.of(
                        "url", stringValue(data.get("url")),
                        "parsed_video", resolved,
                        "market_snapshot", marketSnapshot
                ),
                """
                        返回一个 JSON 对象，字段必须包含：
                        - resolved: 对象
                        - performance: 对象
                        - topic_result: 对象
                        - optimize_result: 对象
                        - copy_result: 对象或 null
                        - analysis: 对象
                        """,
                List.of("hot_board_snapshot"),
                List.of(),
                List.of("resolved", "performance", "topic_result", "optimize_result", "copy_result", "analysis"),
                4
        );
        result.put("resolved", resolved);
        result.putIfAbsent("runtime_mode", "llm_agent");
        return result;
    }

    /**
     * 运行工作台对话模块。
     *
     * @param data 对话上下文与消息
     * @return 对话结果
     */
    public Map<String, Object> runChat(Map<String, Object> data) {
        Map<String, Object> context = map(data.get("context"));
        Map<String, Object> creatorContext = Map.of(
                "field", stringValue(context.get("field")),
                "direction", stringValue(context.get("direction")),
                "idea", stringValue(context.get("idea")),
                "partition", stringValue(context.get("partition")),
                "style", stringValue(context.get("style"))
        );
        String videoUrl = stringValue(context.get("videoLink"));
        Map<String, Object> result = runStructured(
                "workspace_chat",
                "理解用户自然语言意图，自主决定是否调用工具来完成选题、视频分析、热点判断、文案建议等问题，并用中文直接回复。",
                Map.of(
                        "message", stringValue(data.get("message")),
                        "history", data.getOrDefault("history", List.of()),
                        "creator_context", creatorContext,
                        "video_url", videoUrl
                ),
                """
                        返回一个 JSON 对象，字段必须包含：
                        - reply: 字符串
                        - suggested_next_actions: 字符串数组
                        - mode: 固定返回 llm_agent
                        """,
                List.of("creator_briefing", "video_briefing", "hot_board_snapshot"),
                List.of(),
                List.of("reply", "suggested_next_actions", "mode"),
                4
        );
        result.putIfAbsent("mode", "llm_agent");
        return result;
    }

    /**
     * 执行多轮、工具驱动的结构化 Agent 流程。
     *
     * @param taskName 任务名称
     * @param taskGoal 任务目标
     * @param userPayload 用户载荷
     * @param responseContract 最终响应契约
     * @param allowedTools 可用工具列表
     * @param requiredTools 必须调用的工具列表
     * @param requiredFinalKeys 最终结果必填字段
     * @param maxSteps 最大执行步数
     * @return 最终结构化结果
     */
    public Map<String, Object> runStructured(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            List<String> allowedTools,
            List<String> requiredTools,
            List<String> requiredFinalKeys,
            int maxSteps
    ) {
        llmClientService.requireAvailable();
        Map<String, Function<Map<String, Object>, Map<String, Object>>> tools = registeredTools();
        List<Map<String, Object>> scratchpad = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();

        String systemPrompt = """
                你是 B 站创作工作台的 LLM Agent 中枢。
                当前处于严格 LLM 模式：所有分析、判断、决策、生成都必须基于用户输入和工具返回信息实时完成。
                不要套用固定阈值、预设模板、硬编码结论，也不要把任务退回规则引擎。
                你可以多步调用工具；当信息足够时，再输出最终 JSON。
                """;

        for (int step = 0; step < maxSteps; step++) {
            // Each round asks the model to either pick one whitelisted tool or commit a final JSON answer.
            String userPrompt = buildAgentPrompt(taskName, taskGoal, userPayload, responseContract, allowedTools, requiredTools, usedTools, scratchpad);
            JsonNode decision = llmClientService.invokeJsonRequired(systemPrompt, userPrompt);
            String action = JsonUtils.text(decision, "action").trim();
            JsonNode actionInputNode = decision.get("action_input");
            Map<String, Object> actionInput = actionInputNode != null && actionInputNode.isObject()
                    ? objectMapper.convertValue(actionInputNode, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class))
                    : new LinkedHashMap<>();

            if ("final".equals(action)) {
                JsonNode finalNode = decision.get("final");
                if (finalNode == null || !finalNode.isObject()) {
                    scratchpad.add(validationError("final 必须是 JSON 对象"));
                    continue;
                }
                // Final payload validation intentionally mirrors the Python runtime contract.
                Map<String, Object> finalResult = objectMapper.convertValue(
                        finalNode,
                        objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
                );
                List<String> missingTools = requiredTools.stream().filter(tool -> !usedTools.contains(tool)).toList();
                if (!missingTools.isEmpty()) {
                    scratchpad.add(validationError("仍需先调用工具: " + String.join(", ", missingTools)));
                    continue;
                }
                List<String> missingKeys = requiredFinalKeys.stream().filter(key -> !finalResult.containsKey(key)).toList();
                if (!missingKeys.isEmpty()) {
                    scratchpad.add(validationError("最终结果缺少字段: " + String.join(", ", missingKeys)));
                    continue;
                }
                finalResult.putIfAbsent("agent_trace", usedTools);
                finalResult.putIfAbsent("tool_observations", scratchpad);
                finalResult.putIfAbsent("runtime_mode", "llm_agent");
                return finalResult;
            }

            if (!allowedTools.contains(action) || !tools.containsKey(action)) {
                scratchpad.add(validationError("非法工具: " + action));
                continue;
            }

            try {
                Map<String, Object> observation = tools.get(action).apply(actionInput);
                usedTools.add(action);
                scratchpad.add(toolObservation(action, actionInput, observation));
            } catch (Exception exception) {
                scratchpad.add(toolObservation(action, actionInput, Map.of("error", exception.getMessage())));
            }
        }

        throw new IllegalStateException("LLM Agent 未能在限定步骤内完成任务。");
    }

    /**
     * 构建下一轮 Agent 决策提示词。
     *
     * @param taskName 任务名称
     * @param taskGoal 任务目标
     * @param userPayload 用户载荷
     * @param responseContract 最终响应契约
     * @param allowedTools 可用工具列表
     * @param requiredTools 必须调用的工具列表
     * @param usedTools 已调用工具列表
     * @param scratchpad 历史观察结果
     * @return 供模型决策的提示词
     */
    private String buildAgentPrompt(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            List<String> allowedTools,
            List<String> requiredTools,
            List<String> usedTools,
            List<Map<String, Object>> scratchpad
    ) {
        List<String> toolLines = allowedTools.stream().map(tool -> "- " + tool + ": " + toolDescription(tool)).toList();
        return "任务名称：" + taskName + "\n"
                + "任务目标：" + taskGoal + "\n"
                + "用户输入：" + JsonUtils.write(objectMapper, userPayload) + "\n\n"
                + "可用工具：\n" + String.join("\n", toolLines) + "\n\n"
                + "必须至少使用的工具：" + JsonUtils.write(objectMapper, requiredTools) + "\n"
                + "已经使用的工具：" + JsonUtils.write(objectMapper, usedTools) + "\n\n"
                + "历史观察：\n" + scratchpadBlock(scratchpad) + "\n\n"
                + """
                        你必须只返回 JSON 对象，格式如下：
                        {
                          "action": "工具名 或 final",
                          "action_input": {},
                          "final": null 或 最终结果对象
                        }

                        规则：
                        1. 如果信息还不够，action 必须是某个工具名，final 必须是 null。
                        2. 如果 action=final，final 必须完整满足下面的响应契约。
                        3. 不要输出 markdown，不要输出解释，不要输出多余字段。
                        """ + "\n\n最终响应契约：\n" + responseContract;
    }

    /**
     * 将工具调用历史格式化为文本块。
     *
     * @param scratchpad 历史观察结果
     * @return 格式化后的调试文本
     */
    private String scratchpadBlock(List<Map<String, Object>> scratchpad) {
        if (scratchpad.isEmpty()) {
            return "暂无工具调用记录。";
        }
        List<String> blocks = new ArrayList<>();
        for (int index = 0; index < scratchpad.size(); index++) {
            Map<String, Object> item = scratchpad.get(index);
            blocks.add("第 " + (index + 1) + " 步\n"
                    + "action: " + item.get("action") + "\n"
                    + "action_input: " + JsonUtils.write(objectMapper, item.getOrDefault("action_input", Map.of())) + "\n"
                    + "observation: " + JsonUtils.write(objectMapper, item.getOrDefault("observation", Map.of())));
        }
        return String.join("\n\n", blocks);
    }

    /**
     * 返回工具描述文本。
     *
     * @param tool 工具名称
     * @return 工具说明
     */
    private String toolDescription(String tool) {
        return switch (tool) {
            case "creator_briefing" -> "根据领域、方向、想法和分区，抓取热点榜、分区样本、同类样本原始数据。输入: {field, direction, idea, partition}";
            case "video_briefing" -> "解析 B 站视频链接，返回视频公开数据，并抓取相同分区与同类 UP 的原始样本。输入: {url}";
            case "hot_board_snapshot" -> "获取指定分区的热点榜和分区样本原始数据。输入: {partition}";
            default -> "";
        };
    }

    /**
     * 注册可供 Agent 调用的工具集合。
     *
     * @return 工具名称到实现的映射
     */
    private Map<String, Function<Map<String, Object>, Map<String, Object>>> registeredTools() {
        Map<String, Function<Map<String, Object>, Map<String, Object>>> tools = new LinkedHashMap<>();
        tools.put("creator_briefing", this::buildCreatorBriefing);
        tools.put("video_briefing", payload -> buildVideoBriefing(stringValue(payload.get("url"))));
        tools.put("hot_board_snapshot", payload -> buildHotBoardSnapshot(stringValue(payload.getOrDefault("partition", "knowledge"))));
        return tools;
    }

    /**
     * 构建创作者侧简报数据。
     *
     * @param payload 用户输入载荷
     * @return 创作者简报
     */
    private Map<String, Object> buildCreatorBriefing(Map<String, Object> payload) {
        String partition = properties.normalizePartition(stringValue(payload.getOrDefault("partition", "knowledge")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_input", Map.of(
                "field", stringValue(payload.get("field")),
                "direction", stringValue(payload.get("direction")),
                "idea", stringValue(payload.get("idea")),
                "partition", stringValue(payload.getOrDefault("partition", "knowledge")),
                "normalized_partition", partition
        ));
        result.put("market_snapshot", buildMarketSnapshot(partition, List.of()));
        return result;
    }

    /**
     * 构建视频侧简报数据。
     *
     * @param url 视频链接
     * @return 视频简报
     */
    private Map<String, Object> buildVideoBriefing(String url) {
        String bvid = videoResolverService.extractBvid(url);
        Map<String, Object> info = videoResolverService.fetchVideoInfo(url, bvid);
        Map<String, Object> resolved = videoResolverService.buildResolvedPayload(info, bvid);
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("bv_id", bvid);
        video.put("url", url);
        video.put("title", stringValue(resolved.get("title")));
        video.put("up_name", stringValue(resolved.get("up_name")));
        video.put("mid", resolved.getOrDefault("mid", 0));
        video.put("up_ids", resolved.getOrDefault("up_ids", List.of()));
        video.put("tid", resolved.getOrDefault("tid", 0));
        video.put("tname", resolved.getOrDefault("tname", ""));
        video.put("duration", resolved.getOrDefault("duration", 0));
        video.put("stats", resolved.getOrDefault("stats", Map.of()));
        video.put("retrieval_partition", resolved.getOrDefault("partition", "knowledge"));
        video.put("retrieval_partition_label", resolved.getOrDefault("partition_label", "知识"));
        return Map.of(
                "video", video,
                "market_snapshot", buildMarketSnapshot(stringValue(resolved.get("partition")), readIntegerList(resolved.get("up_ids")))
        );
    }

    /**
     * 构建热点榜快照。
     *
     * @param partition 分区名称
     * @return 热点榜快照
     */
    private Map<String, Object> buildHotBoardSnapshot(String partition) {
        Map<String, Object> marketSnapshot = buildMarketSnapshot(partition, List.of());
        return Map.of(
                "partition", marketSnapshot.get("partition"),
                "partition_label", marketSnapshot.get("partition_label"),
                "hot_board", marketSnapshot.get("hot_board"),
                "partition_samples", marketSnapshot.get("partition_samples")
        );
    }

    /**
     * 构建指定分区的市场快照。
     *
     * @param partitionName 分区名称
     * @param upIds 对标 UP 主 ID 列表
     * @return 市场快照
     */
    private Map<String, Object> buildMarketSnapshot(String partitionName, List<Integer> upIds) {
        String normalized = properties.normalizePartition(partitionName);
        return Map.of(
                "partition", normalized,
                "partition_label", videoResolverService.partitionLabel(normalized),
                "source_count", 0,
                "hot_board", topicDataService.serializeVideos(topicDataService.fetchHotVideos().stream().limit(6).toList()),
                "partition_samples", topicDataService.serializeVideos(topicDataService.fetchPartitionVideos(normalized).stream().limit(6).toList()),
                "peer_samples", topicDataService.serializeVideos(topicDataService.fetchPeerUpVideos(upIds).stream().limit(6).toList())
        );
    }

    /**
     * 将原始对象读取为整数列表。
     *
     * @param raw 原始值
     * @return 整数列表
     */
    private List<Integer> readIntegerList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            result.add(com.agent4j.bilibili.util.TextUtils.safeInt(item));
        }
        return result;
    }

    /**
     * 记录一次工具调用观察结果。
     *
     * @param action 工具名称
     * @param actionInput 工具输入
     * @param observation 工具输出
     * @return 结构化观察记录
     */
    private Map<String, Object> toolObservation(String action, Map<String, Object> actionInput, Map<String, Object> observation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("action_input", actionInput);
        payload.put("observation", observation);
        return payload;
    }

    /**
     * 构建校验错误观察记录。
     *
     * @param message 错误消息
     * @return 错误记录
     */
    private Map<String, Object> validationError(String message) {
        return toolObservation("validation_error", Map.of(), Map.of("error", message));
    }

    /**
     * 将对象安全转换为 Map。
     *
     * @param raw 原始值
     * @return 映射结果
     */
    private Map<String, Object> map(Object raw) {
        if (raw instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将对象安全转换为字符串。
     *
     * @param value 原始值
     * @return 去空白后的字符串
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
