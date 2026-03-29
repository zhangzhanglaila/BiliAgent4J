package com.agent4j.bilibili.service;

import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReferenceVideoService {

    private static final Pattern REAL_BVID = Pattern.compile("BV[0-9A-Za-z]{10}", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOPWORDS = Set.of(
            "这个", "那个", "这条", "本期", "今天", "最近", "系列", "合集", "完整", "原创",
            "更新", "日常", "记录", "分享", "推荐", "实拍", "作品", "内容", "视频", "高表现", "爆款", "参考"
    );

    private final BilibiliHttpSupport httpSupport;

    public ReferenceVideoService(BilibiliHttpSupport httpSupport) {
        this.httpSupport = httpSupport;
    }

    public List<Map<String, Object>> buildReferenceVideosFromMarketSnapshot(
            Map<String, Object> marketSnapshot,
            String excludeBvid,
            String queryText,
            Map<String, Object> resolved
    ) {
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.addAll(readItems(marketSnapshot.get("hot_board")));
        sources.addAll(readItems(marketSnapshot.get("peer_samples")));
        sources.addAll(readItems(marketSnapshot.get("partition_samples")));
        return selectReferenceVideos(sources, excludeBvid, 6, queryText, resolved);
    }

    public List<Map<String, Object>> extractReferenceLinksFromToolObservations(
            List<Map<String, Object>> observations,
            String excludeBvid,
            String queryText,
            Map<String, Object> resolved
    ) {
        List<Map<String, Object>> sources = new ArrayList<>();
        List<String> queryParts = new ArrayList<>();
        queryParts.add(queryText);
        for (Map<String, Object> item : observations) {
            Map<String, Object> observation = map(item.get("observation"));
            queryParts.add(extractReferenceQueryFromObservation(observation));
            Map<String, Object> marketSnapshot = map(observation.get("market_snapshot"));
            if (!marketSnapshot.isEmpty()) {
                sources.addAll(readItems(marketSnapshot.get("hot_board")));
                sources.addAll(readItems(marketSnapshot.get("peer_samples")));
                sources.addAll(readItems(marketSnapshot.get("partition_samples")));
            }
        }
        return selectReferenceVideos(sources, excludeBvid, 6, String.join(" ", queryParts), resolved);
    }

    public String buildReferenceQueryText(Map<String, Object> resolved, String extraText) {
        List<String> parts = new ArrayList<>();
        if (resolved != null) {
            for (String key : List.of("title", "topic", "tname", "partition_label")) {
                String value = String.valueOf(resolved.getOrDefault(key, "")).trim();
                if (!value.isBlank() && !parts.contains(value)) {
                    parts.add(value);
                }
            }
        }
        if (extraText != null && !extraText.isBlank()) {
            parts.add(extraText.trim());
        }
        return String.join(" ", parts);
    }

    public List<Map<String, Object>> selectReferenceVideos(
            List<Map<String, Object>> sources,
            String excludeBvid,
            int limit,
            String queryText,
            Map<String, Object> resolved
    ) {
        List<Map<String, Object>> combined = enrichReferenceSourcesWithSearch(sources, queryText, resolved);
        List<RankedReference> entries = new ArrayList<>();
        for (Map<String, Object> item : combined) {
            if (!isRealReferenceVideo(item)) {
                continue;
            }
            entries.add(new RankedReference(item, buildRank(item, queryText, resolved)));
        }
        entries.sort(Comparator.reverseOrder());

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RankedReference ranked : entries) {
            Map<String, Object> item = ranked.item();
            String bvid = String.valueOf(item.getOrDefault("bvid", ""));
            String url = String.valueOf(item.getOrDefault("url", ""));
            if (url.isBlank() || seen.contains(url)) {
                continue;
            }
            if (excludeBvid != null && !excludeBvid.isBlank() && excludeBvid.equalsIgnoreCase(bvid)) {
                continue;
            }
            seen.add(url);
            result.add(Map.of(
                    "title", item.getOrDefault("title", ""),
                    "url", url,
                    "author", item.getOrDefault("author", ""),
                    "cover", item.getOrDefault("cover", ""),
                    "view", TextUtils.safeInt(item.get("view")),
                    "like_rate", TextUtils.safeDouble(item.get("like_rate")),
                    "source", item.getOrDefault("source", "")
            ));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> enrichReferenceSourcesWithSearch(
            List<Map<String, Object>> sources,
            String queryText,
            Map<String, Object> resolved
    ) {
        List<Map<String, Object>> combined = new ArrayList<>();
        if (resolved != null) {
            try {
                combined.addAll(fetchDirectRelatedReferenceVideos(String.valueOf(resolved.getOrDefault("bv_id", "")), 10));
            } catch (Exception ignored) {
            }
        }
        combined.addAll(sources);
        for (String query : buildReferenceSearchQueries(queryText, resolved)) {
            try {
                combined.addAll(fetchSearchReferenceVideos(query, 8));
            } catch (Exception ignored) {
            }
        }
        return combined;
    }

    private List<String> buildReferenceSearchQueries(String queryText, Map<String, Object> resolved) {
        List<String> queries = new ArrayList<>();
        if (resolved != null) {
            String baseTopic = String.valueOf(resolved.getOrDefault("topic", resolved.getOrDefault("title", ""))).trim();
            String partitionLabel = String.valueOf(resolved.getOrDefault("partition_label", resolved.getOrDefault("tname", ""))).trim();
            if (!baseTopic.isBlank()) {
                queries.add(baseTopic.substring(0, Math.min(baseTopic.length(), 50)));
                if (!partitionLabel.isBlank() && !baseTopic.contains(partitionLabel)) {
                    queries.add(baseTopic.substring(0, Math.min(baseTopic.length(), 40)) + " " + partitionLabel);
                }
            }
        }
        String compactQuery = String.join(" ", extractReferenceTerms(queryText));
        if (!compactQuery.isBlank()) {
            queries.add(compactQuery.substring(0, Math.min(compactQuery.length(), 60)));
        }
        return queries.stream().map(String::trim).filter(value -> value.length() >= 2).distinct().limit(5).collect(Collectors.toList());
    }

    private List<String> extractReferenceTerms(String text) {
        String clean = normalizeReferenceText(text);
        List<String> result = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]+|[A-Za-z0-9]+").matcher(clean);
        while (matcher.find()) {
            appendReferenceTerm(result, matcher.group());
        }
        return result.stream().limit(32).collect(Collectors.toList());
    }

    private void appendReferenceTerm(List<String> terms, String term) {
        String value = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2 || value.chars().allMatch(Character::isDigit) || STOPWORDS.contains(value) || terms.contains(value)) {
            return;
        }
        terms.add(value);
    }

    private String normalizeReferenceText(String text) {
        return (text == null ? "" : text)
                .replaceAll("[【】\\[\\]（）()<>《》\"'`~!@#$%^&*_+=|\\\\/:;,.?？！，。、“”·-]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean isRealReferenceVideo(Map<String, Object> item) {
        String bvid = String.valueOf(item.getOrDefault("bvid", ""));
        String url = String.valueOf(item.getOrDefault("url", ""));
        return !Boolean.TRUE.equals(item.get("estimated"))
                && !url.isBlank()
                && REAL_BVID.matcher(bvid).matches();
    }

    private Rank buildRank(Map<String, Object> item, String queryText, Map<String, Object> resolved) {
        String normalizedTitle = normalizeReferenceText(String.valueOf(item.getOrDefault("title", "")));
        List<String> titleTerms = extractReferenceTerms(String.valueOf(item.getOrDefault("title", "")));
        List<String> queryTerms = extractReferenceTerms(queryText);
        List<String> matchedTerms = queryTerms.stream()
                .filter(term -> titleTerms.contains(term) || normalizedTitle.contains(term))
                .distinct()
                .collect(Collectors.toList());

        int overlapScore = matchedTerms.stream().mapToInt(term -> term.length() * term.length()).sum();
        int strongMatchCount = (int) matchedTerms.stream().filter(term -> term.length() >= 4).count();
        int sameUp = resolved != null
                && TextUtils.safeInt(item.get("mid")) > 0
                && TextUtils.safeInt(item.get("mid")) == TextUtils.safeInt(resolved.get("mid")) ? 1 : 0;
        int sameAuthor = resolved != null
                && String.valueOf(item.getOrDefault("author", "")).equals(String.valueOf(resolved.getOrDefault("up_name", ""))) ? 1 : 0;
        String source = String.valueOf(item.getOrDefault("source", ""));
        int sourcePriority = source.contains("当前视频相关推荐") ? 4
                : source.contains("相关搜索") ? 3
                : source.contains("同类UP") ? 2
                : sameUp == 1 || sameAuthor == 1 ? 1
                : source.contains("热榜") ? -1 : 0;
        int related = (!matchedTerms.isEmpty() || sameUp == 1 || sameAuthor == 1) ? 1 : 0;
        return new Rank(
                related,
                strongMatchCount,
                overlapScore,
                sameUp,
                sameAuthor,
                sourcePriority,
                TextUtils.safeDouble(item.get("like_rate")),
                TextUtils.safeInt(item.get("view")),
                -TextUtils.safeDouble(item.get("competition_score")),
                String.valueOf(item.getOrDefault("title", ""))
        );
    }

    private List<Map<String, Object>> fetchDirectRelatedReferenceVideos(String bvid, int limit) {
        if (!REAL_BVID.matcher(bvid == null ? "" : bvid).matches()) {
            return List.of();
        }
        JsonNode payload = httpSupport.fetchJson("https://api.bilibili.com/x/web-interface/archive/related?bvid=" + httpSupport.encode(bvid));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : payload.path("data")) {
            String candidateBvid = item.path("bvid").asText("");
            if (!REAL_BVID.matcher(candidateBvid).matches()) {
                continue;
            }
            JsonNode stat = item.path("stat");
            JsonNode owner = item.path("owner");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("bvid", candidateBvid);
            entry.put("title", httpSupport.stripHtml(item.path("title").asText("")));
            entry.put("author", httpSupport.stripHtml(owner.path("name").asText(item.path("owner_name").asText(""))));
            entry.put("cover", item.path("pic").asText(item.path("cover").asText("")));
            entry.put("mid", TextUtils.safeInt(owner.path("mid").asText("0")));
            entry.put("view", TextUtils.safeInt(stat.path("view").asText("0")));
            entry.put("like", TextUtils.safeInt(stat.path("like").asText("0")));
            entry.put("coin", TextUtils.safeInt(stat.path("coin").asText("0")));
            entry.put("favorite", TextUtils.safeInt(stat.path("favorite").asText("0")));
            entry.put("reply", TextUtils.safeInt(stat.path("reply").asText("0")));
            entry.put("share", TextUtils.safeInt(stat.path("share").asText("0")));
            entry.put("duration", TextUtils.safeInt(item.path("duration").asText("0")));
            entry.put("avg_view_duration", 0.0);
            entry.put("like_rate", TextUtils.safeInt(stat.path("like").asText("0")) / (double) Math.max(TextUtils.safeInt(stat.path("view").asText("0")), 1));
            entry.put("completion_rate", 0.0);
            entry.put("competition_score", 0.0);
            entry.put("source", "当前视频相关推荐");
            entry.put("url", "https://www.bilibili.com/video/" + candidateBvid);
            entry.put("estimated", false);
            result.add(entry);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> fetchSearchReferenceVideos(String query, int limit) {
        JsonNode payload = httpSupport.fetchJson(
                "https://api.bilibili.com/x/web-interface/search/type?search_type=video&order=click&page=1&page_size="
                        + Math.max(1, Math.min(limit, 20))
                        + "&keyword="
                        + httpSupport.encode(query)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : payload.path("data").path("result")) {
            String bvid = item.path("bvid").asText("");
            if (!REAL_BVID.matcher(bvid).matches()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("bvid", bvid);
            entry.put("title", httpSupport.stripHtml(item.path("title").asText("")));
            entry.put("author", httpSupport.stripHtml(item.path("author").asText("")));
            entry.put("cover", item.path("pic").asText(item.path("cover").asText("")));
            entry.put("mid", TextUtils.safeInt(item.path("mid").asText("0")));
            entry.put("view", TextUtils.safeMetricInt(item.path("play").asText("0")));
            entry.put("like", 0);
            entry.put("coin", 0);
            entry.put("favorite", TextUtils.safeMetricInt(item.path("favorites").asText("0")));
            entry.put("reply", TextUtils.safeMetricInt(item.path("review").asText("0")));
            entry.put("share", 0);
            entry.put("duration", TextUtils.safeInt(item.path("duration").asText("0")));
            entry.put("avg_view_duration", 0.0);
            entry.put("like_rate", 0.0);
            entry.put("completion_rate", 0.0);
            entry.put("competition_score", 0.0);
            entry.put("source", "相关搜索:" + query);
            entry.put("url", item.path("arcurl").asText("https://www.bilibili.com/video/" + bvid));
            entry.put("estimated", false);
            result.add(entry);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private String extractReferenceQueryFromObservation(Map<String, Object> observation) {
        Map<String, Object> video = map(observation.get("video"));
        if (!video.isEmpty()) {
            return buildReferenceQueryText(Map.of(
                    "title", video.getOrDefault("title", ""),
                    "topic", video.getOrDefault("title", ""),
                    "tname", video.getOrDefault("tname", ""),
                    "partition_label", video.getOrDefault("retrieval_partition_label", "")
            ), "");
        }
        Map<String, Object> userInput = map(observation.get("user_input"));
        if (!userInput.isEmpty()) {
            return buildReferenceQueryText(Map.of("partition_label", userInput.getOrDefault("partition", "")),
                    String.join(" ",
                            String.valueOf(userInput.getOrDefault("field", "")),
                            String.valueOf(userInput.getOrDefault("direction", "")),
                            String.valueOf(userInput.getOrDefault("idea", ""))));
        }
        return "";
    }

    private List<Map<String, Object>> readItems(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(map(item));
        }
        return result;
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private record RankedReference(Map<String, Object> item, Rank rank) implements Comparable<RankedReference> {
        @Override
        public int compareTo(RankedReference other) {
            return rank.compareTo(other.rank);
        }
    }

    private record Rank(
            int related,
            int strongMatchCount,
            int overlapScore,
            int sameUp,
            int sameAuthor,
            int sourcePriority,
            double likeRate,
            int view,
            double competitionScore,
            String title
    ) implements Comparable<Rank> {
        @Override
        public int compareTo(Rank other) {
            return Comparator.comparingInt(Rank::related)
                    .thenComparingInt(Rank::strongMatchCount)
                    .thenComparingInt(Rank::overlapScore)
                    .thenComparingInt(Rank::sameUp)
                    .thenComparingInt(Rank::sameAuthor)
                    .thenComparingInt(Rank::sourcePriority)
                    .thenComparingDouble(Rank::likeRate)
                    .thenComparingInt(Rank::view)
                    .thenComparingDouble(Rank::competitionScore)
                    .thenComparing(Rank::title)
                    .compare(this, other);
        }
    }
}
