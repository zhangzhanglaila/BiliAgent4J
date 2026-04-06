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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    // 工具超时配置（秒）
    private static final double DEFAULT_TOOL_TIMEOUT_SECONDS = 30.0;
    private final Map<String, Double> toolTimeouts = Map.of(
            "web_search", 45.0,
            "code_interpreter", 60.0,
            "creator_briefing", 30.0,
            "video_briefing", 30.0,
            "hot_board_snapshot", 30.0,
            "retrieval", 20.0
    );

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
        return runStructuredAdvanced(taskName, taskGoal, userPayload, responseContract,
                allowedTools, requiredTools, requiredFinalKeys, maxSteps,
                loadHistory, saveMemory, enableReflection, false);
    }

    /**
     * 高级 LLM Agent 运行方法，支持工具预算和超时控制。
     */
    public Map<String, Object> runStructuredAdvanced(
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
            boolean enableReflection,
            boolean strictToolOrder
    ) {
        llmClientService.requireAvailable();
        Map<String, Function<Map<String, Object>, Map<String, Object>>> tools = registeredTools();
        List<Map<String, Object>> scratchpad = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();
        Map<String, Integer> toolUsageCounts = new LinkedHashMap<>();
        Map<String, Integer> repeatedActionInputs = new LinkedHashMap<>();
        List<String> recentToolActions = new ArrayList<>();
        String memoryUserId = resolveMemoryUserId(taskName, userPayload);
        String queryText = buildQueryText(taskName, userPayload);

        if (loadHistory) {
            autoLoadHistory(memoryUserId, queryText, scratchpad);
        }
        if (allowedTools.contains("retrieval")) {
            autoRetrieve(queryText, scratchpad, usedTools);
        }

        // 构建预算
        Map<String, Object> budget = buildBudget(taskName, allowedTools, maxSteps);

        String systemPrompt = buildSystemPrompt(taskName, taskGoal, userPayload, responseContract,
                allowedTools, requiredTools, strictToolOrder, budget);

        for (int step = 0; step < maxSteps; step++) {
            String userPrompt = buildAgentPrompt(
                    taskName,
                    taskGoal,
                    userPayload,
                    responseContract,
                    allowedTools,
                    requiredTools,
                    usedTools,
                    scratchpad,
                    budget
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
                    applyReflectionAdvanced(taskName, taskGoal, userPayload, responseContract, finalResult, scratchpad, requiredFinalKeys);
                }
                finalResult.putIfAbsent("agent_trace", new ArrayList<>(usedTools));
                finalResult.putIfAbsent("tool_observations", new ArrayList<>(scratchpad));
                finalResult.putIfAbsent("runtime_mode", "llm_agent");
                if (saveMemory) {
                    saveMemoryAsync(memoryUserId, taskName, userPayload, finalResult);
                }
                return finalResult;
            }

            // 检查非法工具
            if (!allowedTools.contains(action) || !tools.containsKey(action)) {
                scratchpad.add(validationError("非法工具: " + action));
                continue;
            }

            // 检查必需工具顺序
            String orderError = checkToolOrderError(action, requiredTools, usedTools, strictToolOrder);
            if (!orderError.isEmpty()) {
                scratchpad.add(toolObservation(action, actionInput, Map.of("error", orderError)));
                continue;
            }

            // 检查工具预算
            String budgetError = checkToolBudgetError(action, actionInput, budget,
                    usedTools.size(), toolUsageCounts, repeatedActionInputs, recentToolActions);
            if (!budgetError.isEmpty()) {
                scratchpad.add(toolObservation(action, actionInput, Map.of("error", budgetError)));
                continue;
            }

            // 执行工具（带超时）
            try {
                Map<String, Object> observation = invokeToolWithTimeout(tools.get(action), action, actionInput);
                usedTools.add(action);
                toolUsageCounts.put(action, toolUsageCounts.getOrDefault(action, 0) + 1);
                recentToolActions.add(action);
                if (recentToolActions.size() > 5) {
                    recentToolActions.remove(0);
                }
                scratchpad.add(toolObservation(action, actionInput, observation));
            } catch (Exception exception) {
                scratchpad.add(toolObservation(action, actionInput, Map.of("error", exception.getMessage())));
            }
        }

        throw new IllegalStateException("LLM Agent 未能在限定步数内完成任务。");
    }

    /**
     * 构建工具预算。
     */
    private Map<String, Object> buildBudget(String taskName, List<String> allowedTools, int maxSteps) {
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("max_steps", Math.max(1, maxSteps));
        budget.put("max_tool_calls", Math.max(1, maxSteps));
        budget.put("repeat_action_limit", 2);

        // per-tool limits
        Map<String, Integer> toolLimits = new LinkedHashMap<>();
        for (String tool : allowedTools) {
            toolLimits.put(tool, Math.max(1, maxSteps));
        }
        budget.put("tool_limits", toolLimits);
        return budget;
    }

    /**
     * 构建系统提示词。
     */
    private String buildSystemPrompt(String taskName, String taskGoal, Map<String, Object> userPayload,
            String responseContract, List<String> allowedTools, List<String> requiredTools,
            boolean strictToolOrder, Map<String, Object> budget) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 B 站创作工作台的 LLM Agent 中枢。\n");
        prompt.append("你必须采用 ReAct 范式：先基于用户输入和已有 observation 思考，再决定是否调用工具，最后输出结构化 JSON。\n");
        prompt.append("所有判断都由你自主完成，不使用硬编码阈值，不依赖固定规则链。\n");
        prompt.append("检索优先但不强制：当本地知识库可能包含历史经验、沉淀资料、案例或已知结构化信息时，优先考虑 retrieval。\n");
        prompt.append("如果问题明显依赖最新公开信息，或 retrieval 返回的信息不足、不匹配、已过期，再调用 web_search。\n");

        // 添加必需工具指引
        if (!requiredTools.isEmpty()) {
            prompt.append("你必须先调用工具获取信息，严禁直接输出 final 结果。\n");
            prompt.append("本任务至少要调用这些工具：").append(requiredTools).append("。\n");
            if (strictToolOrder) {
                prompt.append("必须严格按顺序调用：").append(requiredTools).append("。\n");
            }
        }

        prompt.append("严格遵守工具预算，不要为了凑步骤而无意义调用工具。\n");
        return prompt.toString();
    }

    /**
     * 检查工具顺序错误。
     */
    private String checkToolOrderError(String action, List<String> requiredTools,
            List<String> usedTools, boolean strictOrder) {
        if (!strictOrder || requiredTools.isEmpty() || "final".equals(action)) {
            return "";
        }
        List<String> remaining = requiredTools.stream().filter(tool -> !usedTools.contains(tool)).toList();
        if (remaining.isEmpty()) {
            return "";
        }
        String expectedTool = remaining.get(0);
        if (!action.equals(expectedTool)) {
            return "当前必须先调用工具 " + expectedTool + "，然后才能继续调用 " + action + "。";
        }
        return "";
    }

    /**
     * 检查工具预算错误。
     */
    private String checkToolBudgetError(String action, Map<String, Object> actionInput,
            Map<String, Object> budget, int totalToolCalls,
            Map<String, Integer> toolUsageCounts, Map<String, Integer> repeatedActionInputs,
            List<String> recentToolActions) {

        int maxToolCalls = ((Number) budget.getOrDefault("max_tool_calls", 1)).intValue();
        if (totalToolCalls >= maxToolCalls) {
            return "工具调用总次数已达到上限 " + maxToolCalls + "，请基于现有 observation 完成判断。";
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer> toolLimits = (Map<String, Integer>) budget.getOrDefault("tool_limits", new LinkedHashMap<>());
        int toolLimit = toolLimits.getOrDefault(action, maxToolCalls);
        if (toolUsageCounts.getOrDefault(action, 0) >= toolLimit) {
            return "工具 " + action + " 调用次数已达上限 " + toolLimit + "，请改用其他工具或直接输出 final。";
        }

        int repeatLimit = ((Number) budget.getOrDefault("repeat_action_limit", 1)).intValue();
        String actionSignature = action + ":" + normalizeActionInput(actionInput);
        if (repeatedActionInputs.getOrDefault(actionSignature, 0) >= repeatLimit) {
            return "工具 " + action + " 使用相同参数重复调用已达上限 " + repeatLimit + "，请调整 query 或直接输出 final。";
        }

        if (recentToolActions.size() >= repeatLimit &&
                recentToolActions.stream().skip(recentToolActions.size() - repeatLimit).allMatch(a -> a.equals(action))) {
            return "工具 " + action + " 连续重复调用已达上限 " + repeatLimit + "，请整合已有 observation 后再决策。";
        }

        return "";
    }

    /**
     * 规范化 action input 用于比较。
     */
    private String normalizeActionInput(Map<String, Object> actionInput) {
        try {
            return objectMapper.writeValueAsString(actionInput);
        } catch (Exception e) {
            return String.valueOf(actionInput);
        }
    }

    /**
     * 带超时的工具调用。
     */
    private Map<String, Object> invokeToolWithTimeout(
            Function<Map<String, Object>, Map<String, Object>> toolHandler,
            String toolName,
            Map<String, Object> actionInput) throws Exception {

        double timeoutSeconds = toolTimeouts.getOrDefault(toolName, DEFAULT_TOOL_TIMEOUT_SECONDS);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<String, Object>> future = executor.submit(() -> toolHandler.apply(actionInput));

        try {
            return future.get((long) timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "tool_timeout:" + toolName);
            result.put("timed_out", true);
            result.put("tool", toolName);
            result.put("timeout_seconds", timeoutSeconds);
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 自动加载历史对话记录并追加到 scratchpad。
     * 仅在 queryText 非空时执行，从长期记忆服务检索相关历史。
     * @param memoryUserId 用户标识
     * @param queryText 查询文本
     * @param scratchpad 工具调用记录列表
     */
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

    /**
     * 自动从知识库检索相关内容并追加到 scratchpad。
     * 仅在 queryText 非空时执行，将检索结果作为 observation 记录。
     * @param queryText 查询文本
     * @param scratchpad 工具调用记录列表
     * @param usedTools 已使用工具列表
     */
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

    /**
     * 简单 Reflection：验证最终结果是否满足任务要求。
     * 调用高级 Reflection 逻辑，传入空的 scratchpad 和 requiredFinalKeys。
     * @param taskName 任务名称
     * @param taskGoal 任务目标
     * @param userPayload 用户输入数据
     * @param responseContract 响应契约
     * @param finalResult 最终结果
     */
    private void applyReflection(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            Map<String, Object> finalResult
    ) {
        applyReflectionAdvanced(taskName, taskGoal, userPayload, responseContract, finalResult, List.of(), List.of());
    }

    /**
     * 高级 Reflection：完整的结果质检与可能重写。
     */
    private void applyReflectionAdvanced(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            Map<String, Object> finalResult,
            List<Map<String, Object>> scratchpad,
            List<String> requiredFinalKeys
    ) {
        try {
            JsonNode review = llmClientService.invokeJsonRequired(
                    "你是一个严格的 B 站创作结果审查与重写助手，只返回 JSON。",
                    buildReflectionPrompt(taskName, taskGoal, userPayload, responseContract, finalResult, scratchpad)
            );

            if (review == null) {
                return;
            }

            boolean passed = review.has("pass") && review.get("pass").asBoolean(false);
            if (passed) {
                return; // 通过，不需要修改
            }

            // 尝试使用重写的结果
            JsonNode rewrittenNode = review.get("rewritten_final");
            if (rewrittenNode != null && rewrittenNode.isObject()) {
                List<String> rewriteMissing = validateFinalKeys(
                        objectMapper.convertValue(rewrittenNode, LinkedHashMap.class),
                        requiredFinalKeys
                );

                if (rewriteMissing.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rewritten = new LinkedHashMap<>(
                            objectMapper.convertValue(rewrittenNode, LinkedHashMap.class)
                    );

                    // 添加反思问题
                    if (review.has("issues") && review.get("issues").isArray()) {
                        List<String> issues = new ArrayList<>();
                        review.get("issues").forEach(n -> issues.add(n.asText()));
                        rewritten.put("reflection_issues", issues);
                    }

                    // 替换 finalResult
                    finalResult.clear();
                    finalResult.putAll(rewritten);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 构建反思 prompt。
     */
    private String buildReflectionPrompt(String taskName, String taskGoal,
            Map<String, Object> userPayload, String responseContract,
            Map<String, Object> finalResult, List<Map<String, Object>> scratchpad) {
        return "你是结果质检器，需要判断下面这个 JSON 最终结果是否满足任务要求。\n"
                + "如果满足，pass 返回 true。\n"
                + "如果不满足，pass 返回 false，并直接给出 rewritten_final。\n"
                + "只返回 JSON：{pass:boolean, issues:string[], rewritten_final:object|null}\n\n"
                + "任务名称：" + taskName + "\n"
                + "任务目标：" + taskGoal + "\n"
                + "用户输入：" + JsonUtils.write(objectMapper, userPayload) + "\n"
                + "响应契约：" + responseContract + "\n"
                + "工具观察：" + scratchpadBlock(scratchpad) + "\n"
                + "候选最终结果：" + JsonUtils.write(objectMapper, finalResult);
    }

    /**
     * 验证最终结果是否包含必需字段。
     */
    private List<String> validateFinalKeys(Map<String, Object> finalResult, List<String> requiredFinalKeys) {
        List<String> missing = new ArrayList<>();
        for (String key : requiredFinalKeys) {
            if (!finalResult.containsKey(key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * 异步保存执行结果到长期记忆服务。
     * 将任务名、用户输入和最终结果打包，通过线程池异步写入长期记忆。
     * @param memoryUserId 用户标识
     * @param taskName 任务名称
     * @param userPayload 用户输入数据
     * @param finalResult 最终结果
     */
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

    /**
     * 根据任务名称从用户输入构建检索用查询文本。
     * 不同任务类型拼接不同字段：module_create 拼接领域/方向/创意/分区，
     * module_analyze 拼接 URL/标题/话题/UP主/分区，workspace_chat 拼接消息和创作者上下文。
     * @param taskName 任务名称
     * @param userPayload 用户输入数据
     * @return 拼接后的查询文本
     */
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

    /**
     * 从用户输入中解析长期记忆用的用户标识。
     * 优先使用 payload 中的 memory_user_id，若为空则回退为 taskName。
     * @param taskName 任务名称
     * @param userPayload 用户输入数据
     * @return 用户标识字符串
     */
    private String resolveMemoryUserId(String taskName, Map<String, Object> userPayload) {
        String value = stringValue(userPayload.get("memory_user_id"));
        return value.isBlank() ? taskName : value;
    }

    /**
     * 构建 Agent 提示词的简写重载版本。
     * 委托给完整参数版本，budget 传 null。
     * @param taskName 任务名称
     * @param taskGoal 任务目标
     * @param userPayload 用户输入数据
     * @param responseContract 响应契约
     * @param allowedTools 允许使用的工具列表
     * @param requiredTools 必须使用的工具列表
     * @param usedTools 已使用工具列表
     * @param scratchpad 工具调用历史
     * @return 构建好的提示词文本
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
        return buildAgentPrompt(taskName, taskGoal, userPayload, responseContract,
                allowedTools, requiredTools, usedTools, scratchpad, null);
    }

    /**
     * 构建完整的 Agent 提示词，包含任务信息、工具描述、历史记录和响应契约。
     * 将工具列表格式化为可读描述，拼接 scratchpad 历史，最终加入响应契约要求。
     * @param taskName 任务名称
     * @param taskGoal 任务目标
     * @param userPayload 用户输入数据
     * @param responseContract 响应契约
     * @param allowedTools 允许使用的工具列表
     * @param requiredTools 必须使用的工具列表
     * @param usedTools 已使用工具列表
     * @param scratchpad 工具调用历史
     * @param budget 工具预算信息，可为 null
     * @return 构建好的提示词文本
     */
    private String buildAgentPrompt(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            List<String> allowedTools,
            List<String> requiredTools,
            List<String> usedTools,
            List<Map<String, Object>> scratchpad,
            Map<String, Object> budget
    ) {
        List<String> toolLines = allowedTools.stream().map(tool -> "- " + tool + ": " + toolDescription(tool)).toList();
        StringBuilder prompt = new StringBuilder();
        prompt.append("任务名称: ").append(taskName).append("\n");
        prompt.append("任务目标: ").append(taskGoal).append("\n");
        prompt.append("用户输入: ").append(JsonUtils.write(objectMapper, userPayload)).append("\n\n");
        prompt.append("可用工具:\n").append(String.join("\n", toolLines)).append("\n\n");

        // 添加工具预算信息
        if (budget != null) {
            prompt.append("工具预算:\n");
            prompt.append("- max_steps: ").append(budget.getOrDefault("max_steps", 1)).append("\n");
            prompt.append("- max_tool_calls: ").append(budget.getOrDefault("max_tool_calls", 1)).append("\n");
            prompt.append("- repeat_action_limit: ").append(budget.getOrDefault("repeat_action_limit", 1)).append("\n");
            @SuppressWarnings("unchecked")
            Map<String, Integer> toolLimits = (Map<String, Integer>) budget.getOrDefault("tool_limits", new LinkedHashMap<>());
            prompt.append("- tool_limits: ");
            if (toolLimits.isEmpty()) {
                prompt.append("none");
            } else {
                prompt.append(toolLimits.entrySet().stream()
                        .map(e -> e.getKey() + "<=" + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none"));
            }
            prompt.append("\n\n");
        }

        prompt.append("必须至少使用的工具: ").append(JsonUtils.write(objectMapper, requiredTools)).append("\n");
        prompt.append("已经使用的工具: ").append(JsonUtils.write(objectMapper, usedTools)).append("\n\n");
        prompt.append("历史观察:\n").append(scratchpadBlock(scratchpad)).append("\n\n");
        prompt.append("""
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
                        """).append("\n\n最终响应契约:\n").append(responseContract);
        return prompt.toString();
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
