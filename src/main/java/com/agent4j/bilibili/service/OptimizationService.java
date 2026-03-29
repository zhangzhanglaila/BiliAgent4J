package com.agent4j.bilibili.service;

import com.agent4j.bilibili.model.OptimizationSuggestion;
import com.agent4j.bilibili.model.VideoMetrics;
import com.agent4j.bilibili.repository.VideoMetricRepository;
import com.agent4j.bilibili.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OptimizationService {

    private final VideoResolverService videoResolverService;
    private final VideoMetricRepository repository;
    private final LlmClientService llmClientService;

    public OptimizationService(VideoResolverService videoResolverService, VideoMetricRepository repository, LlmClientService llmClientService) {
        this.videoResolverService = videoResolverService;
        this.repository = repository;
        this.llmClientService = llmClientService;
    }

    public OptimizationSuggestion run(String bvId, List<VideoMetrics> benchmarkVideos) {
        VideoMetrics current = fetchVideoMetrics(bvId);
        List<Map<String, Object>> history = repository.getHistory(bvId, 5);
        RuleDiagnosis rules = ruleBasedDiagnosis(current, benchmarkVideos == null ? List.of() : benchmarkVideos);

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("diagnosis", rules.diagnosis());
        fallback.put("optimized_titles", List.of(
                current.getTitle() + "，30 秒讲清最关键的 3 个点",
                "做对这一步，" + current.getTitle() + " 的效果会完全不一样"
        ));
        fallback.put("cover_suggestion", "封面突出结果对比，使用高反差底色 + 4~6 字大字标题，放大核心收益点。");
        fallback.put("content_suggestions", rules.contentSuggestions());

        String benchmarkSummary = "历史记录 "
                + history.size()
                + " 条；对比样本 "
                + (benchmarkVideos == null ? 0 : benchmarkVideos.size())
                + " 条；当前播放 "
                + current.getView()
                + "，点赞率 "
                + String.format(java.util.Locale.ROOT, "%.2f%%", current.getLikeRate() * 100)
                + "，估算完播率 "
                + String.format(java.util.Locale.ROOT, "%.2f%%", current.getCompletionRate() * 100)
                + "。";

        JsonNode data = llmClientService.invokeJson(
                "你是 B 站增长顾问，请基于数据输出可执行优化建议。",
                "当前视频标题：" + current.getTitle() + "\n"
                        + "当前数据：播放 " + current.getView()
                        + "，点赞 " + current.getLike()
                        + "，投币 " + current.getCoin()
                        + "，收藏 " + current.getFavorite()
                        + "，评论 " + current.getReply()
                        + "，转发 " + current.getShare()
                        + "，时长 " + current.getDuration()
                        + " 秒，估算完播率 " + String.format(java.util.Locale.ROOT, "%.2f%%", current.getCompletionRate() * 100)
                        + "。\n基准总结：" + benchmarkSummary
                        + "\n请返回 JSON，字段包含 diagnosis, optimized_titles(2个), cover_suggestion, content_suggestions。",
                fallback
        );

        OptimizationSuggestion result = new OptimizationSuggestion();
        result.setBvId(bvId);
        result.setDiagnosis(JsonUtils.text(data, "diagnosis").isBlank() ? rules.diagnosis() : JsonUtils.text(data, "diagnosis"));
        result.setOptimizedTitles(readStringList(data.get("optimized_titles"), ((List<String>) fallback.get("optimized_titles"))));
        result.setCoverSuggestion(JsonUtils.text(data, "cover_suggestion").isBlank() ? String.valueOf(fallback.get("cover_suggestion")) : JsonUtils.text(data, "cover_suggestion"));
        result.setContentSuggestions(readStringList(data.get("content_suggestions"), rules.contentSuggestions()));
        result.setBenchmarkSummary(benchmarkSummary);
        result.setRawText(data.toString());
        return result;
    }

    public VideoMetrics fetchVideoMetrics(String bvId) {
        try {
            Map<String, Object> info = videoResolverService.fetchVideoInfo("https://www.bilibili.com/video/" + bvId, bvId);
            Map<String, Object> owner = map(info.get("owner"));
            Map<String, Object> stat = map(info.get("stat"));
            int duration = intValue(info.get("duration"));
            int view = intValue(stat.get("view"));
            int like = intValue(stat.get("like"));
            int coin = intValue(stat.get("coin"));
            int favorite = intValue(stat.get("favorite"));

            VideoMetrics metrics = new VideoMetrics();
            metrics.setBvid(bvId);
            metrics.setTitle(String.valueOf(info.getOrDefault("title", "未知视频")));
            metrics.setAuthor(String.valueOf(owner.getOrDefault("name", "未知UP")));
            metrics.setMid(intValue(owner.get("mid")));
            metrics.setView(view);
            metrics.setLike(like);
            metrics.setCoin(coin);
            metrics.setFavorite(favorite);
            metrics.setReply(intValue(stat.get("reply")));
            metrics.setShare(intValue(stat.get("share")));
            metrics.setDuration(duration);
            metrics.setCompletionRate(estimateCompletionRate(duration, view, like, coin, favorite));
            metrics.setAvgViewDuration(duration * metrics.getCompletionRate());
            metrics.setLikeRate(like / (double) Math.max(view, 1));
            metrics.setSource("目标视频");
            metrics.setUrl("https://www.bilibili.com/video/" + bvId);
            metrics.setExtra(Map.of("estimated", true));
            repository.saveVideoMetrics(metrics);
            return metrics;
        } catch (Exception exception) {
            VideoMetrics fallback = new VideoMetrics();
            fallback.setBvid(bvId);
            fallback.setTitle("演示视频");
            fallback.setAuthor("演示UP");
            fallback.setView(12000);
            fallback.setLike(620);
            fallback.setCoin(180);
            fallback.setFavorite(240);
            fallback.setReply(86);
            fallback.setShare(30);
            fallback.setDuration(180);
            fallback.setAvgViewDuration(88);
            fallback.setLikeRate(620 / 12000.0);
            fallback.setCompletionRate(88 / 180.0);
            fallback.setSource("fallback");
            fallback.setExtra(Map.of("estimated", true));
            repository.saveVideoMetrics(fallback);
            return fallback;
        }
    }

    private RuleDiagnosis ruleBasedDiagnosis(VideoMetrics current, List<VideoMetrics> benchmarkVideos) {
        double avgViews = benchmarkVideos.isEmpty() ? current.getView() : benchmarkVideos.stream().mapToDouble(VideoMetrics::getView).average().orElse(current.getView());
        double avgLikeRate = benchmarkVideos.isEmpty() ? current.getLikeRate() : benchmarkVideos.stream().mapToDouble(VideoMetrics::getLikeRate).average().orElse(current.getLikeRate());
        List<String> diagnosis = new java.util.ArrayList<>();
        List<String> suggestions = new java.util.ArrayList<>();

        if (current.getView() < avgViews * 0.6) {
            diagnosis.add("当前播放明显低于同类爆款，标题和封面吸引力不足。");
            suggestions.add("前 3 秒直接抛出结果或反差观点，减少铺垫。");
        }
        if (current.getCompletionRate() < 0.45) {
            diagnosis.add("估算完播率偏低，开头钩子不够强或中段节奏偏慢。");
            suggestions.add("将开头控制在 8 秒内，并提前预告结尾收益点。");
        }
        if (current.getLikeRate() < avgLikeRate * 0.8) {
            diagnosis.add("点赞率偏低，说明共鸣点或可收藏价值不够。");
            suggestions.add("增加一条可直接复制执行的清单，提高收藏和点赞意愿。");
        }
        if (diagnosis.isEmpty()) {
            diagnosis.add("整体数据正常，建议做标题和封面的轻量 AB 测试。");
            suggestions.add("保持当前内容结构，只测试标题措辞和封面文案。");
        }
        return new RuleDiagnosis(String.join("；", diagnosis), suggestions);
    }

    private double estimateCompletionRate(int duration, int view, int like, int coin, int favorite) {
        if (duration <= 0) {
            return 0.0;
        }
        double weighted = (like * 1.0 + coin * 1.2 + favorite * 1.4) / Math.max(view, 1);
        return Math.min(0.95, 0.22 + weighted * 10);
    }

    private List<String> readStringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> result = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result.isEmpty() ? fallback : result;
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new java.util.LinkedHashMap<>();
    }

    private int intValue(Object value) {
        return com.agent4j.bilibili.util.TextUtils.safeInt(value);
    }

    private record RuleDiagnosis(String diagnosis, List<String> contentSuggestions) {
    }
}
