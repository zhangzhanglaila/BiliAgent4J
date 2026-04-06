package com.agent4j.bilibili.service;

import com.agent4j.bilibili.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 多候选生成与自评分服务。
 * 支持生成多个候选结果并进行 LLM 自评分，选择最优结果。
 * 对标 Python 版本的 generate_multiple 和 _self_score_candidate。
 */
@Service
public class CandidateGenerationService {

    private final LlmClientService llmClientService;
    private final ObjectMapper objectMapper;

    /**
     * 创建候选生成服务。
     *
     * @param llmClientService LLM 客户端服务
     * @param objectMapper JSON 映射器
     */
    public CandidateGenerationService(LlmClientService llmClientService, ObjectMapper objectMapper) {
        this.llmClientService = llmClientService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成多个候选结果并进行评分。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param responseContract 响应契约
     * @param requiredFinalKeys 必需的字段
     * @param candidateCount 候选数量
     * @return 最优结果（包含候选分数列表）
     */
    public Map<String, Object> generateMultiple(
            String systemPrompt,
            String userPrompt,
            String responseContract,
            List<String> requiredFinalKeys,
            int candidateCount
    ) {
        List<Map<String, Object>> candidates = new ArrayList<>();

        for (int i = 0; i < Math.max(1, candidateCount); i++) {
            try {
                JsonNode result = llmClientService.invokeJsonRequired(
                        systemPrompt,
                        userPrompt + "\n\n最终响应契约：\n" + responseContract
                );

                if (result != null && result.isObject()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candidate = objectMapper.convertValue(
                            result,
                            objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
                    );
                    candidates.add(candidate);
                }
            } catch (Exception e) {
                // 静默处理单个候选生成失败
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("未生成可用候选结果。");
        }

        // 对每个候选进行评分
        List<Map<String, Object>> scoredCandidates = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            double score = selfScoreCandidate(candidate, responseContract, requiredFinalKeys);
            scoredCandidates.add(Map.of(
                    "candidate", candidate,
                    "score", score
            ));
        }

        // 按分数降序排序
        scoredCandidates.sort((a, b) -> Double.compare(
                ((Number) b.get("score")).doubleValue(),
                ((Number) a.get("score")).doubleValue()
        ));

        // 取最优结果
        @SuppressWarnings("unchecked")
        Map<String, Object> best = new LinkedHashMap<>((Map<String, Object>) scoredCandidates.get(0).get("candidate"));

        // 添加候选分数列表
        List<Double> scores = new ArrayList<>();
        for (Map<String, Object> scored : scoredCandidates) {
            scores.add(((Number) scored.get("score")).doubleValue());
        }
        best.put("candidate_scores", scores);

        return best;
    }

    /**
     * 对候选结果进行自评分。
     */
    public double selfScoreCandidate(
            Map<String, Object> candidate,
            String responseContract,
            List<String> requiredFinalKeys
    ) {
        double heuristicScore = scoreCandidateHeuristic(candidate, requiredFinalKeys);

        try {
            String payloadText = JsonUtils.write(objectMapper, candidate);
            String reviewPrompt = "请给下面这个候选结果打分，衡量它是否完整、贴合任务、是否像真实可用输出。\n"
                    + "只返回 JSON：{score:number, reason:string}\n\n"
                    + "响应契约：" + responseContract + "\n"
                    + "候选结果：" + payloadText;

            JsonNode review = llmClientService.invokeJsonRequired(
                    "你是结果评分器，只返回 JSON。",
                    reviewPrompt
            );

            if (review == null) {
                return heuristicScore;
            }

            double llmScore = 0.0;
            if (review.has("score") && !review.get("score").isNull()) {
                llmScore = review.get("score").asDouble(0.0);
            }

            if (llmScore <= 0) {
                return heuristicScore;
            }

            // 取启发式评分和 LLM 评分的平均值
            return Math.max(0.0, Math.min(100.0, (heuristicScore + llmScore) / 2));
        } catch (Exception e) {
            return heuristicScore;
        }
    }

    /**
     * 启发式评分。
     */
    private double scoreCandidateHeuristic(Map<String, Object> candidate, List<String> requiredFinalKeys) {
        double score = 100.0;

        // 检查必需字段缺失
        List<String> missingKeys = new ArrayList<>();
        for (String key : requiredFinalKeys) {
            if (!candidate.containsKey(key)) {
                missingKeys.add(key);
            }
        }
        score -= missingKeys.size() * 25;

        // 检查是否有错误标记
        String candidateText = JsonUtils.write(objectMapper, candidate).toLowerCase();
        if (candidateText.contains("error")) {
            score -= 15;
        }

        // 检查内容长度
        if (candidateText.length() < 120) {
            score -= 10;
        }

        return Math.max(score, 0.0);
    }

    /**
     * 验证最终结果是否满足响应契约。
     */
    public List<String> validateFinal(Map<String, Object> finalResult, List<String> requiredFinalKeys) {
        List<String> missingKeys = new ArrayList<>();
        for (String key : requiredFinalKeys) {
            if (!finalResult.containsKey(key)) {
                missingKeys.add(key);
            }
        }
        return missingKeys;
    }

    /**
     * 反思并可能重写最终结果。
     */
    public Map<String, Object> reflectFinal(
            String taskName,
            String taskGoal,
            Map<String, Object> userPayload,
            String responseContract,
            Map<String, Object> finalResult,
            List<Map<String, Object>> scratchpad,
            List<String> requiredFinalKeys
    ) {
        String scratchpadText = buildScratchpadText(scratchpad);

        String reviewPrompt = "你是结果质检器，需要判断下面这个 JSON 最终结果是否满足任务要求。\n"
                + "如果满足，pass 返回 true。\n"
                + "如果不满足，pass 返回 false，并直接给出 rewritten_final。\n"
                + "只返回 JSON：{pass:boolean, issues:string[], rewritten_final:object|null}\n\n"
                + "任务名称：" + taskName + "\n"
                + "任务目标：" + taskGoal + "\n"
                + "用户输入：" + JsonUtils.write(objectMapper, userPayload) + "\n"
                + "响应契约：" + responseContract + "\n"
                + "工具观察：" + scratchpadText + "\n"
                + "候选最终结果：" + JsonUtils.write(objectMapper, finalResult);

        try {
            JsonNode review = llmClientService.invokeJsonRequired(
                    "你是一个严格的 B 站创作结果审查与重写助手，只返回 JSON。",
                    reviewPrompt
            );

            if (review == null) {
                return finalResult;
            }

            // 检查是否通过
            boolean passed = review.has("pass") && review.get("pass").asBoolean(false);
            if (passed) {
                return finalResult;
            }

            // 尝试使用重写的结果
            JsonNode rewrittenNode = review.get("rewritten_final");
            if (rewrittenNode != null && rewrittenNode.isObject()) {
                List<String> rewriteMissing = validateFinal(
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

                    return rewritten;
                }
            }
        } catch (Exception e) {
            // 静默处理反思失败
        }

        return finalResult;
    }

    /**
     * 构建 scratchpad 文本块。
     */
    private String buildScratchpadText(List<Map<String, Object>> scratchpad) {
        if (scratchpad == null || scratchpad.isEmpty()) {
            return "暂无工具调用记录。";
        }

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < scratchpad.size(); index++) {
            Map<String, Object> item = scratchpad.get(index);
            if (index > 0) {
                sb.append("\n\n");
            }
            sb.append("第 ").append(index + 1).append(" 步\n");
            sb.append("action: ").append(item.getOrDefault("action", "")).append("\n");
            sb.append("action_input: ").append(JsonUtils.write(objectMapper, item.getOrDefault("action_input", Map.of()))).append("\n");

            Object observation = item.getOrDefault("observation", Map.of());
            String obsText = JsonUtils.write(objectMapper, observation);
            if (obsText.length() > 3500) {
                obsText = obsText.substring(0, 3500) + "...(truncated)";
            }
            sb.append("observation: ").append(obsText);
        }
        return sb.toString();
    }
}
