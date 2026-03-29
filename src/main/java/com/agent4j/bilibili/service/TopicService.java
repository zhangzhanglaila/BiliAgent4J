package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.model.TopicIdea;
import com.agent4j.bilibili.model.VideoMetrics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TopicService {

    private final TopicDataService topicDataService;
    private final AppProperties properties;

    public TopicService(TopicDataService topicDataService, AppProperties properties) {
        this.topicDataService = topicDataService;
        this.properties = properties;
    }

    public TopicResult run(String partitionName, List<Integer> upIds, String seedTopic) {
        List<VideoMetrics> hotVideos = topicDataService.fetchHotVideos();
        List<VideoMetrics> partitionVideos = topicDataService.fetchPartitionVideos(partitionName);
        List<VideoMetrics> peerVideos = topicDataService.fetchPeerUpVideos(upIds);
        List<VideoMetrics> allVideos = new ArrayList<>();
        allVideos.addAll(hotVideos);
        allVideos.addAll(partitionVideos);
        allVideos.addAll(peerVideos);

        List<TopicIdea> preferred = generateSeedTopics(seedTopic, allVideos, partitionName, upIds);
        List<TopicIdea> trending = generateTrendingTopics(allVideos);
        List<TopicIdea> ideas = mergeIdeas(preferred, trending, 3);

        return new TopicResult(ideas, allVideos.size(), allVideos, seedTopic == null ? "" : seedTopic);
    }

    private List<TopicIdea> generateTrendingTopics(List<VideoMetrics> videos) {
        topicDataService.fillCompetitionScores(videos);
        return videos.stream()
                .sorted(Comparator.comparingDouble(topicDataService::scoreVideo).reversed())
                .map(video -> {
                    List<String> keywords = topicDataService.extractKeywords(video.getTitle());
                    if (keywords.isEmpty()) {
                        return null;
                    }
                    String core = String.join(" / ", keywords.subList(0, Math.min(2, keywords.size())));
                    String reason = "播放 "
                            + video.getView()
                            + "、点赞率 "
                            + String.format(Locale.ROOT, "%.2f%%", video.getLikeRate() * 100)
                            + "、估算完播率 "
                            + String.format(Locale.ROOT, "%.2f%%", video.getCompletionRate() * 100)
                            + "、竞争度 "
                            + String.format(Locale.ROOT, "%.6f", video.getCompetitionScore())
                            + "，说明该题材有流量且竞争相对可控。";
                    return new TopicIdea(
                            core + " 的高效做法",
                            reason,
                            topicDataService.pickVideoType(video.getTitle()),
                            keywords,
                            topicDataService.scoreVideo(video)
                    );
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), this::dedupeTopIdeas));
    }

    private List<TopicIdea> generateSeedTopics(String seedTopic, List<VideoMetrics> videos, String partitionName, List<Integer> upIds) {
        if (seedTopic == null || seedTopic.isBlank()) {
            return List.of();
        }
        String cleaned = cleanSeedTopic(seedTopic);
        if (cleaned.isBlank()) {
            return List.of();
        }
        List<VideoMetrics> relatedVideos = findRelatedVideos(cleaned, videos);
        List<String> keywords = topicDataService.extractKeywords(cleaned);
        if (keywords.isEmpty()) {
            keywords = List.of(cleaned.substring(0, Math.min(cleaned.length(), 12)));
        }
        String baseStyle = topicDataService.pickVideoType(cleaned);
        if (!relatedVideos.isEmpty()) {
            baseStyle = topicDataService.pickVideoType(relatedVideos.get(0).getTitle());
        }

        List<TopicIdea> ideas = new ArrayList<>();
        List<String[]> candidates = buildSeedCandidates(cleaned);
        for (int index = 0; index < candidates.size(); index++) {
            String variant = candidates.get(index)[0];
            String topic = candidates.get(index)[1];
            List<String> ideaKeywords = new ArrayList<>(keywords);
            if (!ideaKeywords.contains(variant)) {
                ideaKeywords.add(variant);
            }
            ideas.add(new TopicIdea(
                    topic,
                    buildSeedReason(cleaned, variant, relatedVideos, partitionName, upIds),
                    baseStyle,
                    ideaKeywords.subList(0, Math.min(6, ideaKeywords.size())),
                    100 - index * 3
            ));
        }
        return ideas;
    }

    private List<TopicIdea> mergeIdeas(List<TopicIdea> preferred, List<TopicIdea> fallback, int limit) {
        List<TopicIdea> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TopicIdea idea : preferred) {
            String key = idea.getTopic().trim().toLowerCase(Locale.ROOT);
            if (!key.isBlank() && seen.add(key)) {
                result.add(idea);
            }
            if (result.size() >= limit) {
                return result;
            }
        }
        for (TopicIdea idea : fallback) {
            String key = idea.getTopic().trim().toLowerCase(Locale.ROOT);
            if (!key.isBlank() && seen.add(key)) {
                result.add(idea);
            }
            if (result.size() >= limit) {
                return result;
            }
        }
        return result;
    }

    private List<TopicIdea> dedupeTopIdeas(List<TopicIdea> ideas) {
        List<TopicIdea> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TopicIdea idea : ideas) {
            String key = idea.getTopic().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                result.add(idea);
            }
            if (result.size() >= 6) {
                break;
            }
        }
        return result;
    }

    private String cleanSeedTopic(String seedTopic) {
        return seedTopic
                .replaceAll("[【\\[].*?[】\\]]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\-_| ]+|[\\-_| ]+$", "")
                .trim();
    }

    private List<VideoMetrics> findRelatedVideos(String seedTopic, List<VideoMetrics> videos) {
        List<String> keywords = topicDataService.extractKeywords(seedTopic);
        if (keywords.isEmpty()) {
            return List.of();
        }
        return videos.stream()
                .filter(video -> keywords.stream().limit(4).anyMatch(keyword -> video.getTitle().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))))
                .sorted(Comparator.comparingDouble(topicDataService::scoreVideo).reversed())
                .collect(Collectors.toList());
    }

    private String buildSeedReason(String seedTopic, String variant, List<VideoMetrics> relatedVideos, String partitionName, List<Integer> upIds) {
        if (!relatedVideos.isEmpty()) {
            double avgViews = relatedVideos.stream().mapToDouble(VideoMetrics::getView).average().orElse(0);
            double avgLikeRate = relatedVideos.stream().mapToDouble(VideoMetrics::getLikeRate).average().orElse(0);
            double avgCompletionRate = relatedVideos.stream().mapToDouble(VideoMetrics::getCompletionRate).average().orElse(0);
            return "围绕当前链接主题“"
                    + seedTopic
                    + "”做 "
                    + variant
                    + "，命中 "
                    + relatedVideos.size()
                    + " 条相关样本；样本平均播放 "
                    + (int) avgViews
                    + "、平均点赞率 "
                    + String.format(Locale.ROOT, "%.2f%%", avgLikeRate * 100)
                    + "、估算完播率 "
                    + String.format(Locale.ROOT, "%.2f%%", avgCompletionRate * 100)
                    + "，适合继续做延展内容。";
        }
        String partitionText = partitionName == null || partitionName.isBlank()
                ? properties.getDefaultPartition()
                : properties.normalizePartition(partitionName);
        int peerCount = upIds == null || upIds.isEmpty() ? properties.defaultPeerUpIds().size() : upIds.size();
        return "围绕当前链接主题“"
                + seedTopic
                + "”做 "
                + variant
                + "，优先结合分区 "
                + partitionText
                + " 和 "
                + peerCount
                + " 个同类 UP 样本做延展，避免直接跟全站热榜撞题。";
    }

    private List<String[]> buildSeedCandidates(String cleaned) {
        String mode = seedTopicMode(cleaned);
        String subject = seedTopicSubject(cleaned);
        String baseSubject = subject.isBlank() ? cleaned : subject;

        if ("dance_first_video".equals(mode)) {
            return List.of(
                    new String[]{"第一条起号", baseSubject + "第一条视频先跳什么更容易起量"},
                    new String[]{"开场动作", baseSubject + "别一上来就硬跳：先做哪种开场动作更容易进推荐"},
                    new String[]{"系列规划", baseSubject + "做系列内容时，第1条、第2条、第3条分别跳什么"}
            );
        }
        if ("opening_hook".equals(mode)) {
            return List.of(
                    new String[]{"开场动作", baseSubject + "前三秒先放什么，更容易把人留下来"},
                    new String[]{"镜头顺序", baseSubject + "别一上来就铺满信息：镜头顺序怎么排更容易进推荐"},
                    new String[]{"系列规划", baseSubject + "想做成系列时，哪一条最适合先发"}
            );
        }
        if ("series_plan".equals(mode)) {
            return List.of(
                    new String[]{"系列规划", baseSubject + "做成系列内容时，第1条、第2条、第3条分别拍什么"},
                    new String[]{"起量切口", baseSubject + "做系列别乱发，先从哪一条开始最容易起量"},
                    new String[]{"互动放大", baseSubject + "做系列时，哪一类互动点最容易带下一条"}
            );
        }
        if ("first_video".equals(mode)) {
            return List.of(
                    new String[]{"第一条起号", baseSubject + "第一条视频先做什么更容易起量"},
                    new String[]{"切口测试", "别直接硬拍" + baseSubject + "：先做哪种切口更容易进推荐"},
                    new String[]{"系列规划", baseSubject + "做成系列内容时，第1条、第2条、第3条分别拍什么"}
            );
        }
        return List.of(
                new String[]{"起量切口", cleaned + "先做哪种切口更容易起量"},
                new String[]{"表达角度", "同样是" + cleaned + "，哪种表达更容易被点进来"},
                new String[]{"系列规划", cleaned + "如果做成系列，下一条最适合拍什么"}
        );
    }

    private String seedTopicMode(String cleaned) {
        if (cleaned.contains("第1条") || cleaned.contains("第2条") || cleaned.contains("第3条") || cleaned.contains("做系列内容")) {
            return "series_plan";
        }
        if (cleaned.contains("开场动作") || cleaned.contains("进推荐") || cleaned.contains("前三秒")) {
            return "opening_hook";
        }
        if (cleaned.contains("先跳什么") || cleaned.contains("跳什么更容易起量")) {
            return "dance_first_video";
        }
        if (cleaned.contains("第一条视频先做什么") || cleaned.contains("第一条视频先拍什么")) {
            return "first_video";
        }
        return "general";
    }

    private String seedTopicSubject(String cleaned) {
        List<String> markers = List.of(
                "第一条视频",
                "第一条",
                "做系列内容时",
                "别一上来就",
                "先做什么",
                "先拍什么",
                "先跳什么",
                "更容易起量",
                "更容易进推荐"
        );
        for (String marker : markers) {
            int index = cleaned.indexOf(marker);
            if (index > 0) {
                return cleaned.substring(0, index).replaceAll("[ ：:，,。]+$", "").trim();
            }
        }
        int splitIndex = cleaned.indexOf("：");
        return splitIndex > 0 ? cleaned.substring(0, splitIndex).trim() : cleaned;
    }

    public record TopicResult(List<TopicIdea> ideas, int sourceCount, List<VideoMetrics> videos, String seedTopic) {
    }
}
