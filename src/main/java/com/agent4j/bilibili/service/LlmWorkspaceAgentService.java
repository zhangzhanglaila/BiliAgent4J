package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class LlmWorkspaceAgentService {

    private final LlmClientService llmClientService;
    private final ObjectMapper objectMapper;
    private final TopicDataService topicDataService;
    private final VideoResolverService videoResolverService;
    private final AppProperties properties;
    private final KnowledgeBaseService knowledgeBaseService;
    private final LongTermMemoryService longTermMemoryService;
    private final SearchToolService searchToolService;
    private final CodeInterpreterService codeInterpreterService;
    private final ExecutorService memoryExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "llm-workspace-memory");
        thread.setDaemon(true);
        return thread;
    });

    public LlmWorkspaceAgentService(
            LlmClientService llmClientService,
            ObjectMapper objectMapper,
            TopicDataService topicDataService,
            VideoResolverService videoResolverService,
            AppProperties properties,
            KnowledgeBaseService knowledgeBaseService,
            LongTermMemoryService longTermMemoryService,
            SearchToolService searchToolService,
            CodeInterpreterService codeInterpreterService
    ) {
        this.llmClientService = llmClientService;
        this.objectMapper = objectMapper;
        this.topicDataService = topicDataService;
        this.videoResolverService = videoResolverService;
        this.properties = properties;
        this.knowledgeBaseService = knowledgeBaseService;
        this.longTermMemoryService = longTermMemoryService;
        this.searchToolService = searchToolService;
        this.codeInterpreterService = codeInterpreterService;
    }

    public Map<String, Object> runModuleCreate(Map<String, Object> data) {
        Map<String, Object> result = runStructured(
                "module_create",
                "基于用户输入和实时市场样本，为创作者输出更容易起量的 3 个选题，并生成完整可发布文案。",
                Map.of(
                        "field", stringValue(data.get("field")),
                        "direction", stringValue(data.get("direction")),
                        "idea", stringValue(data.get("idea")),
                        "partition", stringValue(data.getOrDefault("partition", "knowledge")),
                        "style", stringValue(data.getOrDefault("style", "干货")),
                        "memory_user_id", "web_module_create"
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
                List.of("retrieval", "creator_briefing", "web_search", "code_interpreter"),
                List.of("creator_briefing"),
                List.of("normalized_profile", "seed_topic", "partition", "style", "chosen_topic", "topic_result", "copy_result"),
                3,
                true,
                true,
                true
        );
        result.putIfAbsent("runtime_mode", "llm_agent");
        return result;
    }

    public Map<String, Object> runModuleAnalyze(Map<String, Object> data, Map<String, Object> resolved, Map<String, Object> marketSnapshot) {
        Map<String, Object> result = runStructured(
                "module_analyze",
                "基于后端已经解析出的当前视频真实信息和完整市场样本，结合知识库检索、热点榜单、联网搜索、代码解释器与长期记忆按需补充信息，判断它更接近爆款还是低表现，并输出完整结构化分析。",
                Map.of(
                        "url", stringValue(data.get("url")),
                        "parsed_video", resolved,
                        "market_snapshot", marketSnapshot,
                        "memory_user_id", "web_module_analyze"
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
                List.of("retrieval", "hot_board_snapshot", "web_search", "code_interpreter"),
                List.of(),
                List.of("resolved", "performance", "topic_result", "optimize_result", "copy_result", "analysis"),
                4,
                true,
                true,
                true
        );
        result.put("resolved", resolved);
        result.putIfAbsent("runtime_mode", "llm_agent");
        return result;
    }

    public Map<String, Object> runChat(Map<String, Object> data) {
        Map<String, Object> context = map(data.get("context"));
        Map<String, Object> result = runStructured(
                "workspace_chat",
                "理解用户自然语言意图，自主决定是否调用工具来完成选题、视频分析、热点判断、文案建议等问题，并用中文直接回复。",
                Map.of(
                        "message", stringValue(data.get("message")),
                        "history", data.getOrDefault("history", List.of()),
                        "creator_context", Map.of(
                                "field", stringValue(context.get("field")),
                                "direction", stringValue(context.get("direction")),
                                "idea", stringValue(context.get("idea")),
                                "partition", stringValue(context.get("partition")),
                                "style", stringValue(context.get("style"))
                        ),
                        "video_url", stringValue(context.get("videoLink")),
                        "memory_user_id", "web_workspace_chat"
                ),
                """
                        返回一个 JSON 对象，字段必须包含：
                        - reply: 字符串
                        - suggested_next_actions: 字符串数组
                        - mode: 固定返回 llm_agent
                        """,
                List.of("retrieval", "creator_briefing", "video_briefing", "hot_board_snapshot", "web_search", "code_interpreter"),
                List.of(),
                List.of("reply", "suggested_next_actions", "mode"),
                4,
                true,
                true,
                true
        );
        result.putIfAbsent("mode", "llm_agent");
        return result;
    }

    public Map<String, Object> runStructured(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            List<String> allowedTools,
            List<String> requiredTools,
            List<String> requiredFinalKeys,
            int maxSteps,
            boolean loadHistory,
            boolean saveMemory,
            boolean enableReflection
    ) {
        llmClientService.requireAvailable();
        Map<String, Function<Map<String, Object>, Map<String, Object>>> tools = registeredTools();
        List<Map<String, Object>> scratchpad = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();
        String memoryUserId = resolveMemoryUserId(taskName, userPayload);
        String queryText = buildQueryText(taskName, userPayload);

        if (loadHistory) {
            autoLoadHistory(memoryUserId, queryText, scratchpad);
        }
        if (allowedTools.contains("retrieval")) {
            autoRetrieve(queryText, scratchpad, usedTools);
        }

        String systemPrompt = """
                你是 B 站创作工作台的 LLM Agent 中枢。
                所有分析、判断、决策和生成都必须基于用户输入、历史观察和工具返回的信息实时完成。
                优先利用已经提供的真实 payload，只有在确实需要补充案例、热点、外部公开信息或计算时再调工具。
                当信息足够时，再输出最终 JSON。
                """;

        for (int step = 0; step < maxSteps; step++) {
            String userPrompt = buildAgentPrompt(
                    taskName,
                    taskGoal,
                    userPayload,
                    responseContract,
                    allowedTools,
                    requiredTools,
                    usedTools,
                    scratchpad
            );
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
                if (enableReflection) {
                    applyReflection(taskName, taskGoal, userPayload, responseContract, finalResult);
                }
                finalResult.putIfAbsent("agent_trace", new ArrayList<>(usedTools));
                finalResult.putIfAbsent("tool_observations", new ArrayList<>(scratchpad));
                finalResult.putIfAbsent("runtime_mode", "llm_agent");
                if (saveMemory) {
                    saveMemoryAsync(memoryUserId, taskName, userPayload, finalResult);
                }
                return finalResult;
            }

            if (!allowedTools.contains(action) || !tools.containsKey(action)) {
                scratchpad.add(validationError("非法工具: " + action));
                continue;
            }

            try {
                Map<String, Object> observation = tools.get(action).apply(actionInput);
                if (!usedTools.contains(action)) {
                    usedTools.add(action);
                }
                scratchpad.add(toolObservation(action, actionInput, observation));
            } catch (Exception exception) {
                scratchpad.add(toolObservation(action, actionInput, Map.of("error", exception.getMessage())));
            }
        }

        throw new IllegalStateException("LLM Agent 未能在限定步数内完成任务。");
    }

    private void autoLoadHistory(String memoryUserId, String queryText, List<Map<String, Object>> scratchpad) {
        if (queryText.isBlank()) {
            return;
        }
        Map<String, Object> history = longTermMemoryService.retrieveUserHistory(memoryUserId, queryText, 4);
        scratchpad.add(Map.of(
                "action", "memory_history",
                "action_input", Map.of("user_id", memoryUserId, "query", queryText, "limit", 4),
                "observation", history
        ));
    }

    private void autoRetrieve(String queryText, List<Map<String, Object>> scratchpad, List<String> usedTools) {
        if (queryText.isBlank()) {
            return;
        }
        Map<String, Object> observation = knowledgeBaseService.retrieve(queryText, 4, null);
        if (!usedTools.contains("retrieval")) {
            usedTools.add("retrieval");
        }
        scratchpad.add(toolObservation("retrieval", Map.of("query", queryText, "limit", 4), observation));
    }

    private void applyReflection(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            Map<String, Object> finalResult
    ) {
        try {
            JsonNode review = llmClientService.invokeJsonRequired(
                    "你是结果质检助手。只检查字段完整性、逻辑自洽性和是否偏离任务目标，不要重写无关内容。",
                    "任务名称: " + taskName + "\n"
                            + "任务目标: " + taskGoal + "\n"
                            + "用户输入: " + JsonUtils.write(objectMapper, userPayload) + "\n"
                            + "结果契约: " + responseContract + "\n"
                            + "当前结果: " + JsonUtils.write(objectMapper, finalResult) + "\n\n"
                            + "返回 JSON，对象字段仅包含 issues(字符串数组) 和 optional_patches(对象，可为空)。"
            );
            List<String> issues = readStringList(review.get("issues"));
            if (!issues.isEmpty()) {
                finalResult.put("reflection_issues", issues);
            }
            if (review.get("optional_patches") != null && review.get("optional_patches").isObject()) {
                Map<String, Object> patch = objectMapper.convertValue(
                        review.get("optional_patches"),
                        objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
                );
                for (Map.Entry<String, Object> entry : patch.entrySet()) {
                    finalResult.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveMemoryAsync(String memoryUserId, String taskName, Map<String, Object> userPayload, Map<String, Object> finalResult) {
        memoryExecutor.submit(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("task_name", taskName);
                payload.put("user_payload", userPayload);
                payload.put("final_result", finalResult);
                longTermMemoryService.saveUserData(memoryUserId, payload, taskName);
            } catch (Exception ignored) {
            }
        });
    }

    private String buildQueryText(String taskName, Map<String, Object> userPayload) {
        if ("module_create".equals(taskName)) {
            return String.join(" ",
                    stringValue(userPayload.get("field")),
                    stringValue(userPayload.get("direction")),
                    stringValue(userPayload.get("idea")),
                    stringValue(userPayload.get("partition"))
            ).trim();
        }
        if ("module_analyze".equals(taskName)) {
            Map<String, Object> parsedVideo = map(userPayload.get("parsed_video"));
            return String.join(" ",
                    stringValue(userPayload.get("url")),
                    stringValue(parsedVideo.get("title")),
                    stringValue(parsedVideo.get("topic")),
                    stringValue(parsedVideo.get("up_name")),
                    stringValue(parsedVideo.get("partition"))
            ).trim();
        }
        if ("workspace_chat".equals(taskName)) {
            Map<String, Object> creatorContext = map(userPayload.get("creator_context"));
            return String.join(" ",
                    stringValue(userPayload.get("message")),
                    stringValue(creatorContext.get("field")),
                    stringValue(creatorContext.get("direction")),
                    stringValue(creatorContext.get("idea")),
                    stringValue(creatorContext.get("partition")),
                    stringValue(userPayload.get("video_url"))
            ).trim();
        }
        return JsonUtils.write(objectMapper, userPayload);
    }

    private String resolveMemoryUserId(String taskName, Map<String, Object> userPayload) {
        String value = stringValue(userPayload.get("memory_user_id"));
        return value.isBlank() ? taskName : value;
    }

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
        return "任务名称: " + taskName + "\n"
                + "任务目标: " + taskGoal + "\n"
                + "用户输入: " + JsonUtils.write(objectMapper, userPayload) + "\n\n"
                + "可用工具:\n" + String.join("\n", toolLines) + "\n\n"
                + "必须至少使用的工具: " + JsonUtils.write(objectMapper, requiredTools) + "\n"
                + "已经使用的工具: " + JsonUtils.write(objectMapper, usedTools) + "\n\n"
                + "历史观察:\n" + scratchpadBlock(scratchpad) + "\n\n"
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
                        """ + "\n\n最终响应契约:\n" + responseContract;
    }

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

    private String toolDescription(String tool) {
        return switch (tool) {
            case "creator_briefing" -> "根据领域、方向、想法和分区，抓取热点榜、分区样本、同类样本原始数据。输入: {field, direction, idea, partition}";
            case "video_briefing" -> "解析 B 站视频链接，返回视频公开数据，并抓取相同分区与同类 UP 的样本。输入: {url}";
            case "hot_board_snapshot" -> "获取指定分区的热点榜和分区样本原始数据。输入: {partition}";
            case "retrieval" -> "从本地知识库检索相关经验、案例、工具观察沉淀。输入: {query, limit, metadata_filter}";
            case "web_search" -> "联网搜索热点、平台活动、竞品趋势和外部公开信息。输入: {query, limit}";
            case "code_interpreter" -> "执行 Java 代码片段完成数据处理或计算。输入: {code, variables}";
            default -> "";
        };
    }

    private Map<String, Function<Map<String, Object>, Map<String, Object>>> registeredTools() {
        Map<String, Function<Map<String, Object>, Map<String, Object>>> tools = new LinkedHashMap<>();
        tools.put("creator_briefing", this::buildCreatorBriefing);
        tools.put("video_briefing", payload -> buildVideoBriefing(stringValue(payload.get("url"))));
        tools.put("hot_board_snapshot", payload -> buildHotBoardSnapshot(stringValue(payload.getOrDefault("partition", "knowledge"))));
        tools.put("retrieval", payload -> knowledgeBaseService.retrieve(
                stringValue(payload.get("query")),
                intValue(payload.get("limit"), 4),
                map(payload.get("metadata_filter"))
        ));
        tools.put("web_search", payload -> searchToolService.search(stringValue(payload.get("query")), intValue(payload.get("limit"), 5)));
        tools.put("code_interpreter", codeInterpreterService::run);
        return tools;
    }

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
        saveToolResultToKnowledgeBase(
                "creator_" + stringValue(payload.get("field")) + "_" + stringValue(payload.get("direction")) + "_" + partition,
                JsonUtils.write(objectMapper, result),
                Map.of("source", "creator_briefing", "partition", partition)
        );
        return result;
    }

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

        Map<String, Object> result = Map.of(
                "video", video,
                "market_snapshot", buildMarketSnapshot(stringValue(resolved.get("partition")), readIntegerList(resolved.get("up_ids")))
        );
        saveToolResultToKnowledgeBase(
                "video_" + bvid,
                JsonUtils.write(objectMapper, result),
                Map.of("source", "video_briefing", "partition", stringValue(resolved.get("partition")))
        );
        return result;
    }

    private Map<String, Object> buildHotBoardSnapshot(String partition) {
        Map<String, Object> marketSnapshot = buildMarketSnapshot(partition, List.of());
        Map<String, Object> result = Map.of(
                "partition", marketSnapshot.get("partition"),
                "partition_label", marketSnapshot.get("partition_label"),
                "hot_board", marketSnapshot.get("hot_board"),
                "partition_samples", marketSnapshot.get("partition_samples")
        );
        saveToolResultToKnowledgeBase(
                "hot_" + stringValue(marketSnapshot.get("partition")),
                JsonUtils.write(objectMapper, result),
                Map.of("source", "hot_board_snapshot", "partition", stringValue(marketSnapshot.get("partition")))
        );
        return result;
    }

    private void saveToolResultToKnowledgeBase(String sourceId, String text, Map<String, Object> metadata) {
        try {
            knowledgeBaseService.addDocument(
                    new KnowledgeBaseService.KnowledgeDocument(sourceId, text, metadata)
            );
        } catch (Exception ignored) {
        }
    }

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

    private List<Integer> readIntegerList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            result.add(intValue(item, 0));
        }
        return result;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, Object> toolObservation(String action, Map<String, Object> actionInput, Map<String, Object> observation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("action_input", actionInput);
        payload.put("observation", observation);
        return payload;
    }

    private Map<String, Object> validationError(String message) {
        return toolObservation("validation_error", Map.of(), Map.of("error", message));
    }

    private Map<String, Object> map(Object raw) {
        if (raw instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value == null ? fallback : value).trim());
        } catch (Exception exception) {
            return fallback;
        }
    }
}
