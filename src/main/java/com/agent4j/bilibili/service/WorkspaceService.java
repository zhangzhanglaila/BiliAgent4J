package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.model.CopywritingResult;
import com.agent4j.bilibili.model.OperationResult;
import com.agent4j.bilibili.model.OptimizationSuggestion;
import com.agent4j.bilibili.model.TopicIdea;
import com.agent4j.bilibili.model.VideoMetrics;
import com.agent4j.bilibili.util.JsonUtils;
import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private final AppProperties properties;
    private final TopicService topicService;
    private final TopicDataService topicDataService;
    private final CopywritingService copywritingService;
    private final InteractionService interactionService;
    private final OptimizationService optimizationService;
    private final VideoResolverService videoResolverService;
    private final RuntimeInfoService runtimeInfoService;
    private final ReferenceVideoService referenceVideoService;
    private final LlmClientService llmClientService;
    private final LlmWorkspaceAgentService llmWorkspaceAgentService;

    /**
     * 创建工作台服务并注入所需依赖
     * @param properties 应用配置
     * @param topicService 选题服务
     * @param topicDataService 选题数据服务
     * @param copywritingService 文案服务
     * @param interactionService 互动运营服务
     * @param optimizationService 优化服务
     * @param videoResolverService 视频解析服务
     * @param runtimeInfoService 运行信息服务
     * @param referenceVideoService 参考视频服务
     * @param llmClientService LLM 客户端服务
     * @param llmWorkspaceAgentService 工作台 Agent 服务
     */
    public WorkspaceService(
            AppProperties properties,
            TopicService topicService,
            TopicDataService topicDataService,
            CopywritingService copywritingService,
            InteractionService interactionService,
            OptimizationService optimizationService,
            VideoResolverService videoResolverService,
            RuntimeInfoService runtimeInfoService,
            ReferenceVideoService referenceVideoService,
            LlmClientService llmClientService,
            LlmWorkspaceAgentService llmWorkspaceAgentService
    ) {
        this.properties = properties;
        this.topicService = topicService;
        this.topicDataService = topicDataService;
        this.copywritingService = copywritingService;
        this.interactionService = interactionService;
        this.optimizationService = optimizationService;
        this.videoResolverService = videoResolverService;
        this.runtimeInfoService = runtimeInfoService;
        this.referenceVideoService = referenceVideoService;
        this.llmClientService = llmClientService;
        this.llmWorkspaceAgentService = llmWorkspaceAgentService;
    }

    /**
     * 获取工作台运行时信息
     * @return 当前模式与能力状态
     */
    public Map<String, Object> runtimeInfo() {
        return runtimeInfoService.buildRuntimePayload();
    }

    /**
     * 解析 B 站视频链接并返回基础信息
     * @param url 原始 B 站链接
     * @return 标准化后的视频结构化数据
     */
    public Map<String, Object> resolveBiliLink(String url) {
        return videoResolverService.resolveVideoPayload(url);
    }

    /**
     * 根据分区、UP 主和种子选题生成候选选题
     * @param partitionName 目标分区名称
     * @param upIds 对标 UP 主 ID 列表
     * @param seedTopic 种子选题
     * @return 包含创意列表、来源统计和参考视频的结果
     */
    public Map<String, Object> runTopic(String partitionName, List<Integer> upIds, String seedTopic) {
        TopicService.TopicResult result = topicService.run(partitionName, upIds, seedTopic);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ideas", result.ideas());
        payload.put("source_count", result.sourceCount());
        payload.put("videos", result.videos());
        payload.put("seed_topic", result.seedTopic());
        return payload;
    }

    /**
     * 根据选题和风格生成文案结果
     * @param topic 目标选题
     * @param style 文案风格
     * @return 标题、脚本、简介和标签结果
     */
    public CopywritingResult runCopy(String topic, String style) {
        return copywritingService.run(topic, null, style);
    }

    /**
     * 执行视频互动运营流程
     * @param bvId 目标视频 BV 号
     * @param dryRun 是否仅模拟执行
     * @return 互动操作结果
     */
    public OperationResult runOperate(String bvId, boolean dryRun) {
        return interactionService.processVideoInteractions(bvId, dryRun);
    }

    /**
     * 根据视频表现生成优化建议
     * @param bvId 目标视频 BV 号
     * @return 标题、封面和内容优化建议
     */
    public OptimizationSuggestion runOptimize(String bvId) {
        return optimizationService.run(bvId, List.of());
    }

    /**
     * 串联选题、文案、运营和优化流程
     * @param bvId 目标视频 BV 号
     * @param partitionName 目标分区名称
     * @param upIds 对标 UP 主 ID 列表
     * @param style 文案风格
     * @param seedTopic 种子选题
     * @return 工作流聚合结果
     */
    public Map<String, Object> runPipeline(String bvId, String partitionName, List<Integer> upIds, String style, String seedTopic) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> topicResult = runTopic(partitionName, upIds, seedTopic);
        String topic = extractTopTopic(topicResult, seedTopic);
        payload.put("topic_result", topicResult);
        payload.put("copywriting_result", runCopy(topic, style));
        payload.put("operation_result", runOperate(bvId, true));
        payload.put("optimization_result", runOptimize(bvId));
        return payload;
    }

    /**
     * 构建创作模块结果
     * @param data 创作输入数据
     * @return 选题、文案和创作者画像结果
     */
    public Map<String, Object> moduleCreate(Map<String, Object> data) {
        if (properties.llmEnabled()) {
            try {
                return llmWorkspaceAgentService.runModuleCreate(data);
            } catch (Exception exception) {
                if (llmClientService.shouldSkipSameProviderFallback(exception)) {
                    throw new IllegalStateException("LLM 服务当前不可用：" + llmClientService.formatLlmError(exception));
                }
                Map<String, Object> fallback = runLlmModuleCreateFallback(data);
                fallback.put("llm_warning", "Agent 中枢生成失败，已切换到单次 LLM 回退：" + llmClientService.formatLlmError(exception));
                return fallback;
            }
        }
        String field = stringValue(data.get("field"));
        String direction = stringValue(data.get("direction"));
        String idea = stringValue(data.get("idea"));
        String partition = properties.normalizePartition(stringValue(data.getOrDefault("partition", "knowledge")));
        String style = stringValue(data.getOrDefault("style", "干货"));

        String seedTopic = buildSeedTopic(field, direction, idea);
        Map<String, Object> topicResult = runTopic(partition, List.of(), seedTopic);
        String chosenTopic = extractTopTopic(topicResult, seedTopic);
        CopywritingResult copyResult = runCopy(chosenTopic, style);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seed_topic", seedTopic);
        payload.put("normalized_profile", refineCreatorProfile(field, direction, idea));
        payload.put("partition", partition);
        payload.put("style", style);
        payload.put("topic_result", topicResult);
        payload.put("copy_result", copyResult);
        payload.put("chosen_topic", chosenTopic);
        return payload;
    }

    /**
     * 构建视频分析模块结果
     * @param data 分析输入数据
     * @return 解析信息、表现判断和优化结论
     */
    public Map<String, Object> moduleAnalyze(Map<String, Object> data) {
        String url = stringValue(data.get("url"));
        // Reuse the already resolved payload when it still matches the current link to avoid duplicate parsing.
        Map<String, Object> resolved = videoResolverService.isResolvedPayloadUsable(data.get("resolved"), url)
                ? map(data.get("resolved"))
                : videoResolverService.resolveVideoPayload(url);

        Map<String, Object> marketSnapshot = buildMarketSnapshot(
                stringValue(resolved.get("partition")),
                readIntegerList(resolved.get("up_ids"))
        );

        if (properties.llmEnabled()) {
            try {
                return llmWorkspaceAgentService.runModuleAnalyze(data, resolved, marketSnapshot);
            } catch (Exception exception) {
                if (llmClientService.shouldSkipSameProviderFallback(exception)) {
                    throw new IllegalStateException("LLM 服务当前不可用：" + llmClientService.formatLlmError(exception));
                }
                Map<String, Object> fallback = runLlmModuleAnalyzeFallback(data, resolved, marketSnapshot);
                fallback.put("llm_warning", "Agent 中枢分析失败，已切换到单次 LLM 回退：" + llmClientService.formatLlmError(exception));
                return fallback;
            }
        }

        Map<String, Object> topicResult = runTopic(
                stringValue(resolved.get("partition")),
                readIntegerList(resolved.get("up_ids")),
                stringValue(resolved.get("topic"))
        );
        OptimizationSuggestion optimizeResult = optimizationService.run(
                stringValue(resolved.get("bv_id")),
                castVideoMetrics(topicResult.get("videos"))
        );
        Map<String, Object> performance = classifyVideoPerformance(resolved);
        Object copyResult = null;
        Map<String, Object> analysis;
        if (Boolean.TRUE.equals(performance.get("is_hot"))) {
            analysis = buildHotAnalysis(resolved, performance, topicResult);
        } else {
            copyResult = runCopy(
                    stringValue(resolved.getOrDefault("topic", resolved.getOrDefault("title", "视频优化"))),
                    stringValue(resolved.getOrDefault("style", "干货"))
            );
            analysis = buildLowPerformanceAnalysis(resolved, performance, optimizeResult, topicResult);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resolved", resolved);
        payload.put("performance", performance);
        payload.put("topic_result", topicResult);
        payload.put("optimize_result", optimizeResult);
        payload.put("copy_result", copyResult);
        payload.put("analysis", analysis);
        payload.put("reference_videos", referenceVideoService.buildReferenceVideosFromMarketSnapshot(
                marketSnapshot,
                stringValue(resolved.get("bv_id")),
                referenceVideoService.buildReferenceQueryText(resolved, ""),
                resolved
        ));
        return payload;
    }

    /**
     * 处理工作台智能助手对话
     * @param data 对话输入数据
     * @return 助手回复、建议动作和参考链接
     */
    public Map<String, Object> chat(Map<String, Object> data) {
        if (!properties.llmEnabled()) {
            throw new IllegalStateException("当前是无 Key 规则模式，智能对话助手仅在配置 LLM_API_KEY 后可用。");
        }
        try {
            return llmWorkspaceAgentService.runChat(data);
        } catch (Exception exception) {
            if (llmClientService.shouldSkipSameProviderFallback(exception)) {
                throw new IllegalStateException("智能对话失败：" + llmClientService.formatLlmError(exception));
            }
        }
        String message = stringValue(data.get("message"));
        Map<String, Object> context = map(data.get("context"));
        Map<String, Object> creatorContext = new LinkedHashMap<>();
        creatorContext.put("field", stringValue(context.get("field")));
        creatorContext.put("direction", stringValue(context.get("direction")));
        creatorContext.put("idea", stringValue(context.get("idea")));
        creatorContext.put("partition", stringValue(context.get("partition")));
        creatorContext.put("style", stringValue(context.get("style")));
        String videoUrl = stringValue(context.get("videoLink"));

        String reply = llmClientService.invokeText(
                "你是 B 站创作工作台助手。请结合用户问题和页面上下文，用中文直接回答，并给出下一步建议。",
                "用户问题：" + message + "\n页面上下文：" + JsonUtils.write(new com.fasterxml.jackson.databind.ObjectMapper(), creatorContext) + "\n视频链接：" + videoUrl,
                "我已经拿到你的问题了。你可以继续给我更明确的方向，或者把视频链接直接发给我。"
        );

        List<String> actions = new ArrayList<>();
        if (videoUrl != null && !videoUrl.isBlank()) {
            actions.add("帮我分析这个视频");
        }
        if (!stringValue(creatorContext.get("field")).isBlank()) {
            actions.add("基于当前方向继续给我 3 个选题");
        }
        actions.add("帮我写一版新文案");

        List<Map<String, Object>> references = List.of();
        try {
            if (!videoUrl.isBlank()) {
                Map<String, Object> resolved = videoResolverService.resolveVideoPayload(videoUrl);
                references = referenceVideoService.buildReferenceVideosFromMarketSnapshot(
                        buildMarketSnapshot(stringValue(resolved.get("partition")), readIntegerList(resolved.get("up_ids"))),
                        stringValue(resolved.get("bv_id")),
                        referenceVideoService.buildReferenceQueryText(resolved, message),
                        resolved
                );
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reply", reply);
        payload.put("suggested_next_actions", actions.stream().distinct().limit(3).collect(Collectors.toList()));
        payload.put("mode", "llm_agent");
        payload.put("reference_links", references);
        return payload;
    }

    /**
     * 汇总分区热榜和对标样本构建市场快照
     * @param partitionName 目标分区名称
     * @param upIds 对标 UP 主 ID 列表
     * @return 市场样本快照结果
     */
    public Map<String, Object> buildMarketSnapshot(String partitionName, List<Integer> upIds) {
        String normalized = properties.normalizePartition(partitionName);
        List<VideoMetrics> hotBoard = safeFetch(() -> topicDataService.fetchHotVideos().stream().limit(6).collect(Collectors.toList()));
        List<VideoMetrics> partitionSamples = safeFetch(() -> topicDataService.fetchPartitionVideos(normalized).stream().limit(6).collect(Collectors.toList()));
        List<VideoMetrics> peerSamples = safeFetch(() -> topicDataService.fetchPeerUpVideos(upIds).stream().limit(6).collect(Collectors.toList()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partition", normalized);
        payload.put("partition_label", videoResolverService.partitionLabel(normalized));
        payload.put("source_count", hotBoard.size() + partitionSamples.size() + peerSamples.size());
        payload.put("hot_board", topicDataService.serializeVideos(hotBoard));
        payload.put("partition_samples", topicDataService.serializeVideos(partitionSamples));
        payload.put("peer_samples", topicDataService.serializeVideos(peerSamples));
        return payload;
    }

    /**
     * 根据播放和互动指标判断视频表现
     * @param resolved 已解析视频数据
     * @return 热度标签、评分和原因说明
     */
    public Map<String, Object> classifyVideoPerformance(Map<String, Object> resolved) {
        Map<String, Object> stats = map(resolved.get("stats"));
        int view = TextUtils.safeInt(stats.get("view"));
        int favorite = TextUtils.safeInt(stats.get("favorite"));
        double likeRate = TextUtils.safeDouble(stats.get("like_rate"));
        double coinRate = TextUtils.safeDouble(stats.get("coin_rate"));
        double favoriteRate = TextUtils.safeDouble(stats.get("favorite_rate"));

        int score = 50;
        List<String> reasons = new ArrayList<>();

        if (view >= 500000) {
            score += 18;
            reasons.add("当前播放 " + view + "，已经是明显爆款量级。");
        } else if (view >= 200000) {
            score += 16;
            reasons.add("当前播放 " + view + "，已经具备很强的自然放大能力。");
        } else if (view >= 100000) {
            score += 14;
            reasons.add("当前播放 " + view + "，已经达到明显起量水平。");
        } else if (view >= 50000) {
            score += 11;
            reasons.add("当前播放 " + view + "，处于比较健康的流量区间。");
        } else if (view >= 20000) {
            score += 8;
            reasons.add("当前播放 " + view + "，有一定自然流量基础。");
        } else if (view >= 10000) {
            score += 5;
            reasons.add("当前播放 " + view + "，已经有基础曝光，但离更大放量还有距离。");
        } else if (view >= 3000) {
            score += 2;
            reasons.add("当前播放 " + view + "，还处于早期验证阶段。");
        } else {
            reasons.add("当前播放 " + view + "，整体曝光偏弱，仍有明显提升空间。");
        }

        if (favoriteRate >= 0.03 || favorite >= 5000) {
            score += 16;
            reasons.add("收藏 " + favorite + "、收藏率 " + formatPercent(favoriteRate) + "，说明内容留存价值非常强。");
        } else if (favoriteRate >= 0.02 || favorite >= 2000) {
            score += 13;
            reasons.add("收藏 " + favorite + "、收藏率 " + formatPercent(favoriteRate) + "，内容具备较强复用价值。");
        } else if (favoriteRate >= 0.012 || favorite >= 800) {
            score += 10;
            reasons.add("收藏 " + favorite + "、收藏率 " + formatPercent(favoriteRate) + "，收藏表现已经不错。");
        } else if (favoriteRate >= 0.008 || favorite >= 300) {
            score += 7;
            reasons.add("收藏 " + favorite + "、收藏率 " + formatPercent(favoriteRate) + "，内容开始体现留存价值。");
        } else if (favoriteRate >= 0.004 || favorite >= 100) {
            score += 4;
            reasons.add("收藏 " + favorite + "、收藏率 " + formatPercent(favoriteRate) + "，有一定收藏价值，但还不够强。");
        } else {
            reasons.add("收藏 " + favorite + "、收藏率 " + formatPercent(favoriteRate) + "，说明内容的留存价值还不够突出。");
        }

        if (likeRate >= 0.08) {
            score += 8;
            reasons.add("点赞率 " + formatPercent(likeRate) + "，互动质量很高。");
        } else if (likeRate >= 0.05) {
            score += 6;
            reasons.add("点赞率 " + formatPercent(likeRate) + "，互动质量较高。");
        } else if (likeRate >= 0.03) {
            score += 4;
            reasons.add("点赞率 " + formatPercent(likeRate) + "，基本达到可继续放大的水平。");
        } else if (likeRate >= 0.015) {
            score += 2;
            reasons.add("点赞率 " + formatPercent(likeRate) + "，基础互动尚可。");
        } else {
            reasons.add("点赞率 " + formatPercent(likeRate) + "，说明内容共鸣还不够强。");
        }

        if (coinRate >= 0.008) {
            score += 4;
            reasons.add("投币率 " + formatPercent(coinRate) + "，用户认可度较高。");
        } else if (coinRate >= 0.005) {
            score += 3;
            reasons.add("投币率 " + formatPercent(coinRate) + "，有一定深度认可。");
        } else if (coinRate >= 0.002) {
            score += 1;
            reasons.add("投币率 " + formatPercent(coinRate) + "，有少量高意愿互动。");
        } else {
            reasons.add("投币率 " + formatPercent(coinRate) + "，深度认可仍然偏弱。");
        }

        score = Math.min(96, Math.max(50, score));
        boolean hot = score >= 82;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("label", hot ? "热门爆款" : "播放偏低");
        payload.put("is_hot", hot);
        payload.put("score", score);
        payload.put("reasons", reasons);
        payload.put("summary", hot ? "这条视频更接近热门爆款，可重点拆解它为什么能火。" : "这条视频当前更像播放偏低的视频，优先做针对性优化。");
        return payload;
    }

    /**
     * 为高表现视频整理延展分析
     * @param resolved 已解析视频数据
     * @param performance 表现判断结果
     * @param topicResult 选题结果
     * @return 分析要点和后续选题
     */
    public Map<String, Object> buildHotAnalysis(Map<String, Object> resolved, Map<String, Object> performance, Map<String, Object> topicResult) {
        List<String> followupTopics = readTopicIdeas(topicResult).stream().map(TopicIdea::getTopic).filter(value -> !value.isBlank()).limit(3).collect(Collectors.toList());
        List<String> analysisPoints = new ArrayList<>(readStringList(performance.get("reasons")));
        analysisPoints.addAll(inspectTitleStrength(stringValue(resolved.get("title"))));
        analysisPoints.add("当前分区为 " + stringValue(resolved.getOrDefault("partition_label", resolved.getOrDefault("partition", "未知分区"))) + "，说明视频题材与该分区受众存在较高匹配度。");
        if (!followupTopics.isEmpty()) {
            analysisPoints.add("围绕当前视频继续延展，仍然有可继续放大的选题空间。");
        }
        return Map.of("analysis_points", analysisPoints, "followup_topics", followupTopics);
    }

    /**
     * 为低表现视频整理优化分析
     * @param resolved 已解析视频数据
     * @param performance 表现判断结果
     * @param optimizeResult 优化建议
     * @param topicResult 选题结果
     * @return 问题原因和优化方向结果
     */
    public Map<String, Object> buildLowPerformanceAnalysis(
            Map<String, Object> resolved,
            Map<String, Object> performance,
            OptimizationSuggestion optimizeResult,
            Map<String, Object> topicResult
    ) {
        List<String> nextTopics = readTopicIdeas(topicResult).stream().map(TopicIdea::getTopic).filter(value -> !value.isBlank()).limit(3).collect(Collectors.toList());
        List<String> analysisPoints = new ArrayList<>(readStringList(performance.get("reasons")));
        analysisPoints.add(optimizeResult.getDiagnosis());
        return Map.of(
                "analysis_points", analysisPoints,
                "next_topics", nextTopics,
                "title_suggestions", optimizeResult.getOptimizedTitles().stream().limit(2).collect(Collectors.toList()),
                "cover_suggestion", optimizeResult.getCoverSuggestion(),
                "content_suggestions", optimizeResult.getContentSuggestions().stream().limit(5).collect(Collectors.toList())
        );
    }

    /**
     * 在 Agent 失败时直接调用 LLM 生成创作结果
     * @param data 创作输入数据
     * @return LLM 回退生成结果
     */
    private Map<String, Object> runLlmModuleCreateFallback(Map<String, Object> data) {
        String field = stringValue(data.get("field"));
        String direction = stringValue(data.get("direction"));
        String idea = stringValue(data.get("idea"));
        String partition = properties.normalizePartition(stringValue(data.getOrDefault("partition", "knowledge")));
        String style = stringValue(data.getOrDefault("style", "干货"));

        JsonNode result = llmClientService.invokeJsonRequired(
                "You are a Bilibili topic and copywriting assistant. Return JSON only.",
                "Return one JSON object with these required keys: normalized_profile, seed_topic, partition, style, chosen_topic, topic_result, copy_result.\n"
                        + "user_input=" + JsonUtils.write(new com.fasterxml.jackson.databind.ObjectMapper(), Map.of(
                        "field", field,
                        "direction", direction,
                        "idea", idea,
                        "partition", partition,
                        "style", style
                ))
                        + "\nRules:\n"
                        + "1. partition and style must reuse the current input.\n"
                        + "2. chosen_topic must be concrete and natural.\n"
                        + "3. topic_result.ideas must contain 3 items.\n"
                        + "4. copy_result must include topic, style, titles(3), script(at least 4 sections), description, tags, pinned_comment."
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("normalized_profile", stringOrDefault(result, "normalized_profile", refineCreatorProfile(field, direction, idea)));
        String seedTopic = stringOrDefault(result, "seed_topic", buildSeedTopic(field, direction, idea));
        payload.put("seed_topic", seedTopic);
        payload.put("partition", stringOrDefault(result, "partition", partition));
        payload.put("style", stringOrDefault(result, "style", style));
        payload.put("chosen_topic", stringOrDefault(result, "chosen_topic", seedTopic));
        payload.put("topic_result", result.get("topic_result") == null ? runTopic(partition, List.of(), seedTopic) : toMap(result.get("topic_result")));
        payload.put("copy_result", copywritingService.run(
                stringOrDefault(result, "chosen_topic", seedTopic),
                null,
                stringOrDefault(result, "style", style)
        ));
        payload.put("runtime_mode", "llm_agent");
        payload.put("agent_trace", List.of("llm_direct_fallback"));
        return payload;
    }

    /**
     * 在 Agent 失败时直接调用 LLM 生成分析结果
     * @param data 分析输入数据
     * @param resolved 已解析视频数据
     * @param marketSnapshot 市场快照数据
     * @return LLM 回退分析结果
     */
    private Map<String, Object> runLlmModuleAnalyzeFallback(Map<String, Object> data, Map<String, Object> resolved, Map<String, Object> marketSnapshot) {
        JsonNode result = llmClientService.invokeJsonRequired(
                "你是 B 站视频分析助手。请直接完成爆款/低表现判断、原因拆解、优化建议和后续选题。只返回 JSON。",
                "当前视频真实信息：" + JsonUtils.write(new com.fasterxml.jackson.databind.ObjectMapper(), resolved) + "\n"
                        + "市场样本：" + JsonUtils.write(new com.fasterxml.jackson.databind.ObjectMapper(), marketSnapshot) + "\n"
                        + "要求：resolved, performance, topic_result, optimize_result, copy_result, analysis 都要返回。"
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resolved", resolved);
        payload.put("performance", result.get("performance") == null ? classifyVideoPerformance(resolved) : toMap(result.get("performance")));
        payload.put("topic_result", result.get("topic_result") == null
                ? runTopic(stringValue(resolved.get("partition")), readIntegerList(resolved.get("up_ids")), stringValue(resolved.get("topic")))
                : toMap(result.get("topic_result")));
        payload.put("optimize_result", optimizationService.run(stringValue(resolved.get("bv_id")), List.of()));
        boolean hot = Boolean.TRUE.equals(map(payload.get("performance")).get("is_hot"));
        payload.put("copy_result", hot ? null : runCopy(
                stringValue(resolved.getOrDefault("topic", resolved.getOrDefault("title", "视频优化"))),
                stringValue(resolved.getOrDefault("style", "干货"))
        ));
        payload.put("analysis", hot
                ? buildHotAnalysis(resolved, map(payload.get("performance")), map(payload.get("topic_result")))
                : buildLowPerformanceAnalysis(resolved, map(payload.get("performance")), optimizationService.run(stringValue(resolved.get("bv_id")), List.of()), map(payload.get("topic_result"))));
        payload.put("reference_videos", referenceVideoService.buildReferenceVideosFromMarketSnapshot(
                marketSnapshot,
                stringValue(resolved.get("bv_id")),
                referenceVideoService.buildReferenceQueryText(resolved, ""),
                resolved
        ));
        payload.put("runtime_mode", "llm_agent");
        payload.put("agent_trace", List.of("llm_direct_fallback"));
        return payload;
    }

    /**
     * 组合创作者信息和想法生成种子选题
     * @param field 内容领域
     * @param direction 创作方向
     * @param idea 补充创意
     * @return 种子选题文本
     */
    private String buildSeedTopic(String field, String direction, String idea) {
        String profile = refineCreatorProfile(field, direction, idea);
        String rawIdea = stringValue(idea);
        if (rawIdea.isBlank()) {
            return profile;
        }
        if (rawIdea.contains("怎么") || rawIdea.contains("如何") || rawIdea.contains("为什么") || rawIdea.contains("什么")) {
            return (profile.endsWith("账号") ? profile : profile + "账号") + rawIdea;
        }
        return profile + rawIdea;
    }

    /**
     * 整理创作者领域和方向描述
     * @param field 内容领域
     * @param direction 创作方向
     * @param idea 补充创意
     * @return 归一化后的创作者画像
     */
    private String refineCreatorProfile(String field, String direction, String idea) {
        String profile = (stringValue(field) + stringValue(direction)).replaceAll("\\s+", "");
        String combined = (field + " " + direction + " " + idea);
        if (combined.contains("美女") && (combined.contains("舞") || combined.contains("跳"))) {
            return "颜值向舞蹈账号";
        }
        return profile.isBlank() ? "这类账号" : profile;
    }

    /**
     * 检查标题中的亮点信号
     * @param title 视频标题
     * @return 标题优势说明列表
     */
    private List<String> inspectTitleStrength(String title) {
        List<String> points = new ArrayList<>();
        if (title.matches(".*\\d.*")) {
            points.add("标题里有数字或年份，信息密度更高。");
        }
        if (title.contains("为什么") || title.contains("如何") || title.contains("别再") || title.contains("终于") || title.contains("实测") || title.contains("教程") || title.contains("攻略")) {
            points.add("标题具有明确的问题导向或结果导向。");
        }
        if (title.contains("！") || title.contains("?") || title.contains("？")) {
            points.add("标题带有情绪张力或悬念。");
        }
        if (title.length() >= 8 && title.length() <= 28) {
            points.add("标题长度适中，表达相对集中。");
        }
        if (points.isEmpty()) {
            points.add("标题主题明确，但还可以继续强化结果感和反差感。");
        }
        return points;
    }

    /**
     * 将原始对象安全转换为视频指标列表
     * @param raw 原始列表对象
     * @return 视频指标列表
     */
    @SuppressWarnings("unchecked")
    private List<VideoMetrics> castVideoMetrics(Object raw) {
        if (raw instanceof List<?> list) {
            return (List<VideoMetrics>) list;
        }
        return List.of();
    }

    /**
     * 从选题结果中提取首个选题
     * @param topicResult 选题结果
     * @param fallback 回退选题
     * @return 优先使用的选题文本
     */
    private String extractTopTopic(Map<String, Object> topicResult, String fallback) {
        List<TopicIdea> ideas = readTopicIdeas(topicResult);
        if (!ideas.isEmpty()) {
            return ideas.get(0).getTopic();
        }
        return fallback == null || fallback.isBlank() ? "B站内容选题" : fallback;
    }

    /**
     * 从选题结果中读取创意列表
     * @param topicResult 选题结果
     * @return 选题创意对象列表
     */
    private List<TopicIdea> readTopicIdeas(Map<String, Object> topicResult) {
        Object raw = topicResult.get("ideas");
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof TopicIdea) {
            return list.stream().map(item -> (TopicIdea) item).collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * 将原始列表转换为整数列表
     * @param raw 原始列表对象
     * @return 整数列表
     */
    private List<Integer> readIntegerList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(TextUtils::safeInt).collect(Collectors.toList());
    }

    /**
     * 将原始列表转换为字符串列表
     * @param raw 原始列表对象
     * @return 字符串列表
     */
    private List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).collect(Collectors.toList());
    }

    /**
     * 将任意映射对象转换为字符串键 Map
     * @param raw 原始映射对象
     * @return 转换后的 Map 结果
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
     * 将 JsonNode 转换为 Map 结构
     * @param node JSON 节点
     * @return 转换后的 Map 结果
     */
    private Map<String, Object> toMap(JsonNode node) {
        return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                node,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                }
        );
    }

    /**
     * 将对象转换为去空白字符串
     * @param value 原始值
     * @return 字符串结果
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 读取 JsonNode 字段并提供默认值
     * @param node JSON 节点
     * @param field 字段名
     * @param fallback 默认值
     * @return 字符串结果
     */
    private String stringOrDefault(JsonNode node, String field, String fallback) {
        String value = JsonUtils.text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 将小数格式化为百分比字符串
     * @param value 百分比小数值
     * @return 百分比文本
     */
    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100);
    }

    /**
     * 安全执行数据拉取并在失败时返回空列表
     * @param supplier 数据拉取回调
     * @return 结果列表
     */
    private <T> List<T> safeFetch(java.util.concurrent.Callable<List<T>> supplier) {
        try {
            return supplier.call();
        } catch (Exception exception) {
            return List.of();
        }
    }
}
