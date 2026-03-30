package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.model.TopicIdea;
import com.agent4j.bilibili.model.VideoMetrics;
import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TopicDataService {

    private final AppProperties properties;
    private final BilibiliHttpSupport httpSupport;

    /**
     * 创建选题数据服务。
     *
     * @param properties 系统配置
     * @param httpSupport B 站请求支持组件
     */
    public TopicDataService(AppProperties properties, BilibiliHttpSupport httpSupport) {
        this.properties = properties;
        this.httpSupport = httpSupport;
    }

    /**
     * 抓取 B 站全站热门视频样本。
     *
     * @return 热门视频指标列表
     */
    public List<VideoMetrics> fetchHotVideos() {
        JsonNode payload = httpSupport.fetchJson("https://api.bilibili.com/x/web-interface/popular?pn=1&ps=20");
        List<VideoMetrics> result = new ArrayList<>();
        for (JsonNode item : payload.path("data").path("list")) {
            result.add(buildMetrics(item, "全站热榜"));
            if (result.size() >= 20) {
                break;
            }
        }
        return result;
    }

    /**
     * 抓取指定分区的榜单视频样本。
     *
     * @param partitionName 分区名称
     * @return 分区视频指标列表
     */
    public List<VideoMetrics> fetchPartitionVideos(String partitionName) {
        String normalized = properties.normalizePartition(partitionName);
        int tid = properties.partitionTid(normalized);
        JsonNode payload = httpSupport.fetchJson("https://api.bilibili.com/x/web-interface/ranking/v2?rid=" + tid + "&type=all");
        List<VideoMetrics> result = new ArrayList<>();
        for (JsonNode item : payload.path("data").path("list")) {
            result.add(buildMetrics(item, "分区热榜:" + normalized));
            if (result.size() >= 10) {
                break;
            }
        }
        return result;
    }

    /**
     * 抓取对标 UP 主的近期视频样本。
     *
     * @param upIds 对标 UP 主 ID 列表
     * @return 对标视频指标列表
     */
    public List<VideoMetrics> fetchPeerUpVideos(List<Integer> upIds) {
        List<Integer> targets = (upIds == null || upIds.isEmpty()) ? properties.defaultPeerUpIds() : upIds;
        List<VideoMetrics> result = new ArrayList<>();
        for (Integer upId : targets) {
            if (upId == null || upId <= 0) {
                continue;
            }
            try {
                JsonNode payload = httpSupport.fetchJson(
                        "https://api.bilibili.com/x/space/arc/search?mid="
                                + upId
                                + "&pn=1&ps=5&order=pubdate"
                );
                for (JsonNode item : payload.path("data").path("list").path("vlist")) {
                    result.add(buildPeerMetric(item, upId));
                    if (result.size() >= 15) {
                        return result;
                    }
                }
            } catch (Exception ignored) {
            }
            sleepInterval();
        }
        return result;
    }

    /**
     * 将视频指标列表转换为可序列化结构。
     *
     * @param videos 视频指标列表
     * @return 序列化后的结果列表
     */
    public List<Map<String, Object>> serializeVideos(List<VideoMetrics> videos) {
        return videos.stream().map(this::serializeVideoMetric).collect(Collectors.toList());
    }

    /**
     * 将单个视频指标转换为前端结构。
     *
     * @param videoMetric 视频指标对象
     * @return 序列化后的指标数据
     */
    public Map<String, Object> serializeVideoMetric(VideoMetrics videoMetric) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bvid", videoMetric.getBvid());
        payload.put("title", videoMetric.getTitle());
        payload.put("author", videoMetric.getAuthor());
        payload.put("cover", videoMetric.getCover());
        payload.put("mid", videoMetric.getMid());
        payload.put("view", videoMetric.getView());
        payload.put("like", videoMetric.getLike());
        payload.put("coin", videoMetric.getCoin());
        payload.put("favorite", videoMetric.getFavorite());
        payload.put("reply", videoMetric.getReply());
        payload.put("share", videoMetric.getShare());
        payload.put("duration", videoMetric.getDuration());
        payload.put("avg_view_duration", videoMetric.getAvgViewDuration());
        payload.put("like_rate", videoMetric.getLikeRate());
        payload.put("completion_rate", videoMetric.getCompletionRate());
        payload.put("competition_score", videoMetric.getCompetitionScore());
        payload.put("source", videoMetric.getSource());
        payload.put("url", videoMetric.getUrl());
        payload.put("estimated", Boolean.TRUE.equals(videoMetric.getExtra().get("estimated")));
        return payload;
    }

    /**
     * 根据互动表现估算平均观看时长。
     *
     * @param duration 视频时长
     * @param view 播放量
     * @param like 点赞量
     * @param favorite 收藏量
     * @param reply 评论量
     * @return 估算后的平均观看秒数
     */
    public double estimateAverageViewDuration(int duration, int view, int like, int favorite, int reply) {
        if (duration <= 0) {
            return 0.0;
        }
        double engagement = (like * 1.0 + favorite * 1.5 + reply * 2.0) / Math.max(view, 1);
        double estimatedRatio = Math.min(0.92, 0.25 + engagement * 8);
        return duration * estimatedRatio;
    }

    /**
     * 解析时长文本为秒数。
     *
     * @param raw 原始时长文本
     * @return 时长秒数
     */
    public int parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String[] parts = raw.split(":");
        if (parts.length == 1) {
            return TextUtils.safeInt(parts[0]);
        }
        if (parts.length == 2) {
            return TextUtils.safeInt(parts[0]) * 60 + TextUtils.safeInt(parts[1]);
        }
        return TextUtils.safeInt(parts[0]) * 3600 + TextUtils.safeInt(parts[1]) * 60 + TextUtils.safeInt(parts[2]);
    }

    /**
     * 从标题中提取关键词。
     *
     * @param title 视频标题
     * @return 关键词列表
     */
    public List<String> extractKeywords(String title) {
        return TextUtils.extractKeywords(
                title == null ? "" : title.replaceAll("[^\\w\\u4e00-\\u9fff]", " "),
                6
        );
    }

    /**
     * 根据标题关键词判断视频类型。
     *
     * @param title 视频标题
     * @return 推断出的视频类型
     */
    public String pickVideoType(String title) {
        Map<String, String> mapping = Map.ofEntries(
                Map.entry("教程", "教学"),
                Map.entry("入门", "教学"),
                Map.entry("原理", "干货"),
                Map.entry("实战", "干货"),
                Map.entry("搞笑", "搞笑"),
                Map.entry("整活", "搞笑"),
                Map.entry("混剪", "混剪"),
                Map.entry("盘点", "混剪"),
                Map.entry("复盘", "干货"),
                Map.entry("测评", "干货")
        );
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (title != null && title.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "干货";
    }

    /**
     * 根据流量和互动指标计算视频分数。
     *
     * @param video 视频指标
     * @return 综合评分
     */
    public double scoreVideo(VideoMetrics video) {
        double traffic = Math.log10(Math.max(video.getView(), 1));
        double interaction = video.getLikeRate() * 100 + video.getCompletionRate() * 10;
        double competitionPenalty = 1 / Math.max(video.getCompetitionScore() * 100000 + 1, 1);
        return traffic + interaction + competitionPenalty;
    }

    /**
     * 按关键词桶为视频补充竞争分数。
     *
     * @param videos 视频指标列表
     */
    public void fillCompetitionScores(List<VideoMetrics> videos) {
        Map<String, List<VideoMetrics>> buckets = new LinkedHashMap<>();
        for (VideoMetrics video : videos) {
            List<String> tags = extractKeywords(video.getTitle());
            String key = tags.isEmpty() ? "通用" : tags.get(0);
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(video);
        }
        for (List<VideoMetrics> group : buckets.values()) {
            double totalView = group.stream().mapToDouble(VideoMetrics::getView).sum();
            double competition = group.size() / Math.max(totalView, 1.0);
            for (VideoMetrics video : group) {
                video.setCompetitionScore(competition);
            }
        }
    }

    /**
     * 根据接口条目构建视频指标对象。
     *
     * @param item 接口返回条目
     * @param source 数据来源标识
     * @return 视频指标对象
     */
    private VideoMetrics buildMetrics(JsonNode item, String source) {
        JsonNode stat = item.path("stat");
        JsonNode owner = item.path("owner");
        int duration = TextUtils.safeInt(item.path("duration").asText(item.path("duration_seconds").asText("0")));
        int view = TextUtils.safeInt(stat.path("view").asText(item.path("play").asText("0")));
        int like = TextUtils.safeInt(stat.path("like").asText("0"));
        int favorite = TextUtils.safeInt(stat.path("favorite").asText("0"));
        int coin = TextUtils.safeInt(stat.path("coin").asText("0"));
        int reply = TextUtils.safeInt(stat.path("reply").asText("0"));
        int share = TextUtils.safeInt(stat.path("share").asText("0"));
        double avgViewDuration = estimateAverageViewDuration(duration, view, like, favorite, reply);

        VideoMetrics metrics = new VideoMetrics();
        metrics.setBvid(item.path("bvid").asText(item.path("short_link_v2").asText("")));
        metrics.setTitle(item.path("title").asText("未知标题"));
        metrics.setAuthor(owner.path("name").asText(item.path("author").asText("未知UP")));
        metrics.setCover(item.path("pic").asText(item.path("cover").asText(item.path("thumbnail").asText(""))));
        metrics.setMid(TextUtils.safeInt(owner.path("mid").asText(item.path("mid").asText("0"))));
        metrics.setView(view);
        metrics.setLike(like);
        metrics.setCoin(coin);
        metrics.setFavorite(favorite);
        metrics.setReply(reply);
        metrics.setShare(share);
        metrics.setDuration(duration);
        metrics.setAvgViewDuration(avgViewDuration);
        metrics.setLikeRate(like / (double) Math.max(view, 1));
        metrics.setCompletionRate(duration <= 0 ? 0.0 : Math.min(1.0, avgViewDuration / Math.max(duration, 1)));
        metrics.setSource(source);
        metrics.setPubdate(TextUtils.safeLong(item.path("pubdate").asText("0")));
        metrics.setUrl(metrics.getBvid().isBlank() ? "" : "https://www.bilibili.com/video/" + metrics.getBvid());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("estimated", true);
        metrics.setExtra(extra);
        return metrics;
    }

    /**
     * 根据对标 UP 视频条目构建指标对象。
     *
     * @param item 接口返回条目
     * @param upId 对标 UP 主 ID
     * @return 视频指标对象
     */
    private VideoMetrics buildPeerMetric(JsonNode item, int upId) {
        VideoMetrics metrics = new VideoMetrics();
        metrics.setBvid(item.path("bvid").asText(""));
        metrics.setTitle(item.path("title").asText(""));
        metrics.setAuthor(item.path("author").asText("同类UP"));
        metrics.setMid(upId);
        metrics.setView(TextUtils.safeInt(item.path("play").asText("0")));
        metrics.setLike(TextUtils.safeInt(item.path("comment").asText("0")) * 3);
        metrics.setCoin(metrics.getView() / 100);
        metrics.setFavorite(metrics.getView() / 80);
        metrics.setReply(TextUtils.safeInt(item.path("comment").asText("0")));
        metrics.setShare(metrics.getView() / 200);
        metrics.setDuration(parseDuration(item.path("length").asText("")));
        metrics.setAvgViewDuration(estimateAverageViewDuration(
                metrics.getDuration(),
                metrics.getView(),
                metrics.getLike(),
                metrics.getFavorite(),
                metrics.getReply()
        ));
        metrics.setCompletionRate(metrics.getDuration() <= 0
                ? 0.0
                : Math.min(1.0, metrics.getAvgViewDuration() / Math.max(metrics.getDuration(), 1)));
        metrics.setLikeRate(metrics.getLike() / (double) Math.max(metrics.getView(), 1));
        metrics.setCover(item.path("pic").asText(""));
        metrics.setSource("同类UP:" + upId);
        metrics.setUrl("https://www.bilibili.com/video/" + metrics.getBvid());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("estimated", true);
        metrics.setExtra(extra);
        return metrics;
    }

    /**
     * 按配置暂停一段时间，避免请求过快。
     */
    private void sleepInterval() {
        try {
            Thread.sleep((long) (properties.getRequestInterval() * 1000));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
