package com.agent4j.bilibili.service;

import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class VideoResolverService {

    private final BilibiliHttpSupport httpSupport;

    /**
     * 创建视频解析服务并注入网络支持
     * @param httpSupport B 站网络请求支持
     */
    public VideoResolverService(BilibiliHttpSupport httpSupport) {
        this.httpSupport = httpSupport;
    }

    /**
     * 从视频链接中提取 BV 号
     * @param url 原始视频链接
     * @return 规范化后的 BV 号
     */
    public String extractBvid(String url) {
        String raw = url == null ? "" : url.trim();
        String candidate = httpSupport.resolveShortLink(raw);
        Matcher matcher = BilibiliHttpSupport.BVID_PATTERN.matcher(candidate);
        if (matcher.find()) {
            String value = matcher.group(1);
            return "BV" + value.substring(2);
        }
        if (raw.contains("b23.tv") || raw.contains("bili2233.cn")) {
            throw new IllegalArgumentException("短链展开失败，请改用包含 BV 号的完整视频链接重试");
        }
        throw new IllegalArgumentException("未识别到有效的 B 站视频 BV 号");
    }

    /**
     * 解析视频链接并构建标准化载荷
     * @param url 原始视频链接
     * @return 工作台使用的视频结构化数据
     */
    public Map<String, Object> resolveVideoPayload(String url) {
        String bvid = extractBvid(url);
        Map<String, Object> info = fetchVideoInfo(url, bvid);
        return buildResolvedPayload(info, bvid);
    }

    /**
     * 拉取目标视频的原始信息
     * @param url 原始视频链接
     * @param bvid 视频 BV 号
     * @return 原始视频信息
     */
    public Map<String, Object> fetchVideoInfo(String url, String bvid) {
        List<String> errors = new ArrayList<>();
        try {
            return fetchVideoInfoViaPublicApi(bvid);
        } catch (Exception exception) {
            errors.add("public api: " + exception.getMessage());
        }
        try {
            return fetchVideoInfoViaHtml(url, bvid);
        } catch (Exception exception) {
            errors.add("html: " + exception.getMessage());
        }
        throw new IllegalArgumentException(String.join("；", errors));
    }

    /**
     * 将原始视频信息整理为工作台结构
     * @param info 原始视频信息
     * @param bvid 视频 BV 号
     * @return 标准化视频载荷
     */
    public Map<String, Object> buildResolvedPayload(Map<String, Object> info, String bvid) {
        Map<String, Object> owner = map(info.get("owner"));
        int mid = TextUtils.safeInt(owner.get("mid"));
        String upName = text(owner, "name");
        if (upName.isBlank()) {
            upName = text(owner, "uname");
        }
        String title = text(info, "title");
        int tid = TextUtils.safeInt(info.get("tid"));
        String tname = text(info, "tname");
        Map<String, Object> stats = extractVideoStats(info);
        String partition = mapPartition(tname, tid);
        String topic = buildTopic(title);
        String style = guessStyle(title, partition, tname);
        String partitionLabel = partitionLabel(partition);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bv_id", bvid);
        result.put("mid", mid);
        result.put("up_ids", mid > 0 ? List.of(mid) : List.of());
        result.put("up_name", upName);
        result.put("cover", text(info, "pic").isBlank() ? text(info, "cover") : text(info, "pic"));
        result.put("partition", partition);
        result.put("partition_label", partitionLabel);
        result.put("tid", tid);
        result.put("tname", tname);
        result.put("title", title);
        result.put("topic", topic);
        result.put("style", style);
        result.put("duration", TextUtils.safeInt(info.get("duration")));
        result.put("stats", stats);
        result.put("summary", (upName.isBlank() ? "未知UP" : upName) + " · " + (tname.isBlank() ? partitionLabel : tname));
        return result;
    }

    /**
     * 判断已解析载荷是否还能复用
     * @param payload 已解析载荷
     * @param url 当前视频链接
     * @return 是否可直接复用
     */
    public boolean isResolvedPayloadUsable(Object payload, String url) {
        if (!(payload instanceof Map<?, ?> raw)) {
            return false;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        raw.forEach((key, item) -> value.put(String.valueOf(key), item));
        String bvId = text(value, "bv_id");
        String title = text(value, "title");
        Object stats = value.get("stats");
        if (bvId.isBlank() || title.isBlank() || !(stats instanceof Map<?, ?>)) {
            return false;
        }
        try {
            String expectedBvid = extractBvid(url);
            return expectedBvid.isBlank() || expectedBvid.equalsIgnoreCase(bvId);
        } catch (Exception exception) {
            return true;
        }
    }

    /**
     * 将分区编码转换为中文标签
     * @param partition 分区编码
     * @return 分区中文名称
     */
    public String partitionLabel(String partition) {
        return switch (partition) {
            case "knowledge" -> "知识";
            case "tech" -> "科技";
            case "life" -> "生活";
            case "game" -> "游戏";
            case "ent" -> "娱乐";
            default -> partition;
        };
    }

    /**
     * 根据分区名称和 tid 映射系统分区
     * @param tname B 站原始分区名
     * @param tid B 站原始分区 ID
     * @return 工作台分区编码
     */
    public String mapPartition(String tname, int tid) {
        String text = tname == null ? "" : tname.toLowerCase();
        if (text.contains("知识") || text.contains("科普") || text.contains("学习") || text.contains("职业")) {
            return "knowledge";
        }
        if (text.contains("科技") || text.contains("数码") || text.contains("软件") || text.contains("程序")) {
            return "tech";
        }
        if (text.contains("游戏") || text.contains("电竞")) {
            return "game";
        }
        if (text.contains("生活") || text.contains("美食") || text.contains("日常") || text.contains("家居")) {
            return "life";
        }
        if (text.contains("娱乐") || text.contains("影视") || text.contains("综艺") || text.contains("音乐")) {
            return "ent";
        }
        if (List.of(36, 201, 208, 209, 229).contains(tid)) {
            return "knowledge";
        }
        if (List.of(95, 122, 124).contains(tid)) {
            return "tech";
        }
        if (List.of(4, 17, 65, 136, 172).contains(tid)) {
            return "game";
        }
        if (List.of(21, 76, 138, 160).contains(tid)) {
            return "life";
        }
        if (List.of(5, 71, 137, 181).contains(tid)) {
            return "ent";
        }
        return "knowledge";
    }

    /**
     * 根据标题和分区推断内容风格
     * @param title 视频标题
     * @param partition 工作台分区编码
     * @param tname B 站原始分区名
     * @return 推断出的风格名称
     */
    public String guessStyle(String title, String partition, String tname) {
        String text = ((title == null ? "" : title) + " " + (tname == null ? "" : tname)).toLowerCase();
        if (text.contains("教程") || text.contains("教学") || text.contains("入门") || text.contains("攻略") || text.contains("如何") || text.contains("怎么")) {
            return "教学";
        }
        if (text.contains("搞笑") || text.contains("整活") || text.contains("吐槽") || text.contains("沙雕")) {
            return "搞笑";
        }
        if (text.contains("混剪") || text.contains("高燃") || text.contains("mad") || text.contains("卡点")) {
            return "混剪";
        }
        if ("game".equals(partition) && text.contains("攻略")) {
            return "教学";
        }
        return "干货";
    }

    /**
     * 根据标题生成可用选题文本
     * @param title 视频标题
     * @return 清理后的选题文本
     */
    public String buildTopic(String title) {
        String value = title == null ? "" : title;
        value = value.replaceAll("[【\\[].*?[】\\]]", "");
        value = value.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("^[\\-_| ]+|[\\-_| ]+$", "");
        return value.isBlank() ? "B站内容选题拆解" : value;
    }

    /**
     * 提取播放、点赞和收藏等统计信息
     * @param info 原始视频信息
     * @return 统计字段结果
     */
    public Map<String, Object> extractVideoStats(Map<String, Object> info) {
        Map<String, Object> stat = map(info.get("stat"));
        int view = TextUtils.safeInt(stat.get("view"));
        if (view <= 0) {
            view = TextUtils.safeInt(info.get("play"));
        }
        int like = TextUtils.safeInt(stat.get("like"));
        int coin = TextUtils.safeInt(stat.get("coin"));
        int favorite = TextUtils.safeInt(stat.get("favorite"));
        int reply = TextUtils.safeInt(stat.get("reply"));
        int share = TextUtils.safeInt(stat.get("share"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("view", view);
        result.put("like", like);
        result.put("coin", coin);
        result.put("favorite", favorite);
        result.put("reply", reply);
        result.put("share", share);
        result.put("like_rate", like / (double) Math.max(view, 1));
        result.put("coin_rate", coin / (double) Math.max(view, 1));
        result.put("favorite_rate", favorite / (double) Math.max(view, 1));
        return result;
    }

    /**
     * 通过公开接口获取视频信息
     * @param bvid 视频 BV 号
     * @return 原始视频信息
     */
    private Map<String, Object> fetchVideoInfoViaPublicApi(String bvid) {
        JsonNode payload = httpSupport.fetchJson("https://api.bilibili.com/x/web-interface/view?bvid=" + httpSupport.encode(bvid));
        if (payload.path("code").asInt(0) != 0) {
            throw new IllegalArgumentException("B站公开视频接口失败: " + payload.path("message").asText("unknown"));
        }
        JsonNode data = payload.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalArgumentException("B站公开视频接口未返回视频详情");
        }
        return httpSupport.mapOf(data);
    }

    /**
     * 通过页面 HTML 回退获取视频信息
     * @param url 原始视频链接
     * @param bvid 视频 BV 号
     * @return 原始视频信息
     */
    private Map<String, Object> fetchVideoInfoViaHtml(String url, String bvid) {
        List<String> candidates = List.of(url == null ? "" : url.trim(), "https://www.bilibili.com/video/" + bvid);
        List<String> errors = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.isBlank()) {
                continue;
            }
            try {
                String html = httpSupport.fetchText(candidate, Duration.ofSeconds(12));
                return normalizeHtmlInfo(html, bvid);
            } catch (Exception exception) {
                errors.add(candidate + ": " + exception.getMessage());
            }
        }
        throw new IllegalArgumentException("网页源码解析失败: " + String.join("；", errors));
    }

    /**
     * 从 HTML 中提取并规范化视频信息
     * @param html 页面 HTML 内容
     * @param bvid 视频 BV 号
     * @return 规范化后的原始视频信息
     */
    private Map<String, Object> normalizeHtmlInfo(String html, String bvid) {
        Document document = Jsoup.parse(html);
        JsonNode state = httpSupport.extractInitialState(html);
        JsonNode videoData = state.path("videoData");
        if (videoData.isMissingNode() || videoData.isNull()) {
            videoData = state.path("videoInfo");
        }
        JsonNode owner = videoData.path("owner");
        JsonNode stat = videoData.path("stat");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aid", TextUtils.safeLong(httpSupport.value(videoData, "aid", httpSupport.extractRegex(html, "\"aid\"\\s*:\\s*(\\d+)"))));
        result.put("title", httpSupport.value(videoData, "title", httpSupport.meta(document, "meta[property=og:title]")));
        result.put("tid", TextUtils.safeInt(httpSupport.value(videoData, "tid", httpSupport.extractRegex(html, "\"tid\"\\s*:\\s*(\\d+)"))));
        result.put("tname", httpSupport.value(videoData, "tname", httpSupport.extractRegex(html, "\"tname\"\\s*:\\s*\"([^\"]*)\"")));
        result.put("pic", httpSupport.value(videoData, "pic", httpSupport.meta(document, "meta[property=og:image]")));
        result.put("duration", TextUtils.safeInt(httpSupport.value(videoData, "duration", httpSupport.extractRegex(html, "\"duration\"\\s*:\\s*(\\d+)"))));

        Map<String, Object> ownerMap = new LinkedHashMap<>();
        ownerMap.put("mid", TextUtils.safeInt(httpSupport.value(owner, "mid", httpSupport.extractRegex(html, "\"mid\"\\s*:\\s*(\\d+)"))));
        ownerMap.put("name", httpSupport.value(owner, "name", httpSupport.value(state.path("upData"), "name", "")));
        result.put("owner", ownerMap);

        Map<String, Object> statMap = new LinkedHashMap<>();
        statMap.put("view", TextUtils.safeInt(httpSupport.value(stat, "view", httpSupport.extractRegex(html, "\"view\"\\s*:\\s*(\\d+)"))));
        statMap.put("like", TextUtils.safeInt(httpSupport.value(stat, "like", httpSupport.extractRegex(html, "\"like\"\\s*:\\s*(\\d+)"))));
        statMap.put("coin", TextUtils.safeInt(httpSupport.value(stat, "coin", httpSupport.extractRegex(html, "\"coin\"\\s*:\\s*(\\d+)"))));
        statMap.put("favorite", TextUtils.safeInt(httpSupport.value(stat, "favorite", httpSupport.extractRegex(html, "\"favorite\"\\s*:\\s*(\\d+)"))));
        statMap.put("reply", TextUtils.safeInt(httpSupport.value(stat, "reply", httpSupport.extractRegex(html, "\"reply\"\\s*:\\s*(\\d+)"))));
        statMap.put("share", TextUtils.safeInt(httpSupport.value(stat, "share", httpSupport.extractRegex(html, "\"share\"\\s*:\\s*(\\d+)"))));
        result.put("stat", statMap);
        result.put("bvid", bvid);
        return result;
    }

    /**
     * 将任意映射对象转换为字符串键 Map
     * @param value 原始映射对象
     * @return 转换后的 Map 结果
     */
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 读取映射中的字符串字段
     * @param map 数据映射
     * @param key 字段名
     * @return 字符串结果
     */
    private String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
