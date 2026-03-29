package com.agent4j.bilibili.service;

import com.agent4j.bilibili.model.CopywritingResult;
import com.agent4j.bilibili.model.TopicIdea;
import com.agent4j.bilibili.util.JsonUtils;
import com.agent4j.bilibili.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CopywritingService {

    private static final Map<String, String> STYLE_GUIDE = Map.of(
            "干货", "表达清晰，节奏快，强调结论先行和可执行步骤。",
            "教学", "像老师带着做一遍，步骤明确，适合新手。",
            "搞笑", "保留信息量，同时加入轻松调侃和反差包袱。",
            "混剪", "强调高能片段、节奏感和视觉冲击。"
    );

    private static final Map<String, String> STYLE_ENDING = Map.of(
            "干货", "如果你想看我继续拆同类题材，评论区留一个方向，我按实战继续做。",
            "教学", "如果你想让我把这个题材拆成拍摄清单，评论区留“继续”，我下一条直接给模板。",
            "搞笑", "如果你也踩过这种坑，评论区打一个“我也这样”，我继续把后面两条内容排给你。",
            "混剪", "如果你想看我把镜头节奏和卡点顺序继续拆开，评论区留题材，我下一条直接排镜头。"
    );

    private final LlmClientService llmClientService;

    public CopywritingService(LlmClientService llmClientService) {
        this.llmClientService = llmClientService;
    }

    public CopywritingResult run(String topic, TopicIdea topicIdea, String style) {
        String finalTopic = cleanText(topic == null || topic.isBlank()
                ? topicIdea == null ? "B站高效运营" : topicIdea.getTopic()
                : topic);
        String finalStyle = cleanText(style).isBlank() ? "干货" : cleanText(style);

        CopywritingResult fallback = fallback(finalTopic, finalStyle);
        Map<String, Object> fallbackJson = new LinkedHashMap<>();
        fallbackJson.put("titles", fallback.getTitles());
        fallbackJson.put("script", fallback.getScript());
        fallbackJson.put("description", fallback.getDescription());
        fallbackJson.put("tags", fallback.getTags());
        fallbackJson.put("pinned_comment", fallback.getPinnedComment());

        JsonNode data = llmClientService.invokeJson(
                "你是 B 站百万粉 UP 主的文案总监，输出能直接发布的视频文案。",
                "主题：" + finalTopic + "\n"
                        + "风格：" + finalStyle + "\n"
                        + "风格要求：" + STYLE_GUIDE.getOrDefault(finalStyle, STYLE_GUIDE.get("干货")) + "\n"
                        + "请基于创作者场景生成自然、可直接口播和发布的 JSON，避免复述输入词，避免机械使用“高效做法”等空泛表达。"
                        + " 字段为：titles(3个标题), script(数组，每项包含 section/duration/content), description, tags(10-15个), pinned_comment。",
                fallbackJson
        );

        CopywritingResult result = new CopywritingResult();
        result.setTopic(finalTopic);
        result.setStyle(finalStyle);
        result.setTitles(pickTitles(data, fallback));
        result.setScript(pickScript(data, fallback));
        result.setDescription(cleanText(JsonUtils.text(data, "description")).isBlank()
                ? fallback.getDescription()
                : cleanText(JsonUtils.text(data, "description")));
        result.setTags(pickTags(data, fallback));
        String pinned = cleanText(JsonUtils.text(data, "pinned_comment"));
        result.setPinnedComment(pinned.isBlank() ? fallback.getPinnedComment() : pinned);
        result.setRawText(data.toString());
        return result;
    }

    public String cleanText(String text) {
        return TextUtils.cleanCopyText(text == null ? "" : text);
    }

    public CopywritingResult fallback(String topic, String style) {
        CopywritingResult result = new CopywritingResult();
        result.setTopic(topic);
        result.setStyle(style);
        result.setTitles(buildTitles(topic, style));
        result.setScript(buildScript(topic, style));
        result.setDescription(buildDescription(topic, style));
        result.setTags(buildTags(topic, style));
        result.setPinnedComment(buildPinnedComment(topic));
        result.setRawText("fallback");
        return result;
    }

    private List<String> buildTitles(String topic, String style) {
        String mode = topicMode(topic);
        String subject = extractSubject(topic);
        String accountSubject = accountSubject(subject);
        String contentSubject = contentSubject(subject);
        if ("dance_first_video".equals(mode)) {
            return List.of(
                    "想做" + accountSubject + "，第一条先跳什么才更容易进推荐",
                    "别一上来就上难度，" + accountSubject + "第一条先跳这几类动作",
                    "同样是" + contentSubject + "，为什么这种开场更容易被看完"
            );
        }
        if ("opening_hook".equals(mode)) {
            return List.of(
                    accountSubject + "别一上来就硬跳，这种开场动作更容易进推荐",
                    "3 秒先留人再发力：" + contentSubject + "开场该怎么设计",
                    "做" + contentSubject + "最容易掉播放的，不是动作难度，是开场顺序"
            );
        }
        if ("series_plan".equals(mode)) {
            return List.of(
                    accountSubject + "做系列别乱发，第1条到第3条这样排更容易起量",
                    "想把" + contentSubject + "做成系列，先把前三条内容顺序定好",
                    contentSubject + "连续发三条时，每一条分别承担什么作用"
            );
        }
        if ("first_video".equals(mode)) {
            return List.of(
                    "新号做" + contentSubject + "，第一条先拍什么更容易拿到推荐",
                    "别直接上最难的，" + contentSubject + "第一条先做这个切口",
                    "想把" + contentSubject + "做起来，第一条视频先解决这件事"
            );
        }
        if ("教学".equals(style)) {
            return List.of(
                    contentSubject + "新手第一条该怎么拍，顺序我给你排好了",
                    "想做" + contentSubject + "，先按这个结构拍，试错成本最低",
                    contentSubject + "别乱开题，先从最容易验证的一条开始"
            );
        }
        if ("搞笑".equals(style)) {
            return List.of(
                    contentSubject + "别上来就自嗨，这种拍法更容易被看完",
                    "同样是做" + contentSubject + "，为什么有人一发就有量",
                    contentSubject + "第一条别硬冲，这种切口更容易出效果"
            );
        }
        if ("混剪".equals(style)) {
            return List.of(
                    contentSubject + "第一条怎么剪，前三秒高能位要这样放",
                    "想做" + contentSubject + "混剪，先把镜头顺序排对",
                    contentSubject + "为什么总留不住人，问题通常出在第一屏"
            );
        }
        return List.of(
                contentSubject + "先做哪种切口，更容易被推荐",
                "同样是" + contentSubject + "，这类表达为什么更容易起量",
                contentSubject + "想做成系列，先从这一条开始"
        );
    }

    private List<Map<String, String>> buildScript(String topic, String style) {
        String mode = topicMode(topic);
        String subject = extractSubject(topic);
        String accountSubject = accountSubject(subject);
        String contentSubject = contentSubject(subject);
        String ending = STYLE_ENDING.getOrDefault(style, STYLE_ENDING.get("干货"));

        if ("dance_first_video".equals(mode)) {
            return List.of(
                    CopywritingResult.scriptItem("开头钩子", "0-8 秒", "如果你正准备做" + accountSubject + "，第一条别急着上完整编舞。先选 3 秒内能看懂、动作识别度高、镜头能立住的内容，更容易拿到第一波推荐。"),
                    CopywritingResult.scriptItem("动作选择", "8-28 秒", "优先拍节奏明确、上手不难、能带表情管理的动作。太难的编排会拖慢更新，也不利于你快速测出观众偏好。"),
                    CopywritingResult.scriptItem("镜头节奏", "28-58 秒", "开头先给最抓眼的定格或转身，中段补一个近景表情点，结尾留一句互动提问，比如“下一条想看我跳哪种风格”。"),
                    CopywritingResult.scriptItem("结尾引导", "58-75 秒", ending)
            );
        }
        if ("opening_hook".equals(mode)) {
            return List.of(
                    CopywritingResult.scriptItem("开头钩子", "0-8 秒", "做" + contentSubject + "最容易犯的错，就是上来直接全身远景开拍。前三秒先留人，再展示完整动作，推荐系统才更容易把你送出去。"),
                    CopywritingResult.scriptItem("前三秒设计", "8-26 秒", "先给半身近景、明显节奏点或一句字幕反差，把观众先拽住，再接主动作。前三秒的任务不是展示全部，而是制造停留。"),
                    CopywritingResult.scriptItem("中段推进", "26-56 秒", "中段把最稳的动作和表情管理放在一起，结尾补一个评论区问题或下一条预告，让这一条既能看完，也能带出后续内容。"),
                    CopywritingResult.scriptItem("结尾引导", "56-75 秒", ending)
            );
        }
        if ("series_plan".equals(mode)) {
            return List.of(
                    CopywritingResult.scriptItem("开头钩子", "0-8 秒", "想把" + contentSubject + "做成系列，别第一条就把所有东西全塞进去。前三条要分工，不然账号很难建立稳定记忆点。"),
                    CopywritingResult.scriptItem("第 1 条作用", "8-26 秒", "第一条负责让观众记住你的人设和最强记忆点，内容要简单、明确、好理解，不要先堆复杂信息。"),
                    CopywritingResult.scriptItem("第 2 条和第 3 条", "26-58 秒", "第二条放大第一条里反馈最好的动作或表达，第三条再补变化和互动。这样你能更快判断哪种内容值得继续放大。"),
                    CopywritingResult.scriptItem("结尾引导", "58-78 秒", ending)
            );
        }
        if ("first_video".equals(mode)) {
            return List.of(
                    CopywritingResult.scriptItem("开头钩子", "0-8 秒", "新号做" + contentSubject + "，第一条别想着面面俱到。先解决一个最具体、最容易让人停下来的问题，比堆信息更重要。"),
                    CopywritingResult.scriptItem("切口选择", "8-30 秒", "优先选一个用户一看就懂的切口，比如结果对比、常见误区、第一步怎么做。切口越具体，推荐和点击越容易稳定。"),
                    CopywritingResult.scriptItem("内容结构", "30-60 秒", "开头给结果，中段拆原因，结尾留下条后续延展方向。第一条的目标不是讲完，而是让观众愿意继续看你下一条。"),
                    CopywritingResult.scriptItem("结尾引导", "60-78 秒", ending)
            );
        }
        return List.of(
                CopywritingResult.scriptItem("开头钩子", "0-8 秒", "这条先把结论放前面：做" + contentSubject + "，先别贪多，先拿一个最容易验证的切口去测反馈。"),
                CopywritingResult.scriptItem("核心观点 1", "8-28 秒", "先讲观众为什么会停下来，再讲你具体要给什么结果。只要这两件事对上，内容方向就不会跑偏。"),
                CopywritingResult.scriptItem("核心观点 2", "28-56 秒", "把最容易出效果的表达放在前半段，把补充说明放在后半段。顺序排对，完播和互动通常都会比平铺直叙更好。"),
                CopywritingResult.scriptItem("结尾引导", "56-75 秒", ending)
        );
    }

    private String buildDescription(String topic, String style) {
        String mode = topicMode(topic);
        String subject = contentSubject(extractSubject(topic));
        Map<String, String> summaries = Map.of(
                "dance_first_video", "重点拆第一条起号该拍什么、动作怎么选、镜头顺序怎么排。",
                "opening_hook", "重点拆前三秒怎么留人、开场动作怎么设计、节奏怎么推进。",
                "series_plan", "重点拆前三条内容怎么分工，避免系列内容一上来就散。",
                "first_video", "重点拆第一条视频的切口、结构和后续承接方式。",
                "general", "重点拆选题切口、表达结构和互动设计。"
        );
        return "本条围绕“" + topic + "”展开，适合正在做 " + subject + " 的创作者参考。"
                + summaries.getOrDefault(mode, summaries.get("general"))
                + " 文案风格是“" + style + "”，可直接按段落改成自己的版本。";
    }

    private List<String> buildTags(String topic, String style) {
        String mode = topicMode(topic);
        String subject = contentSubject(extractSubject(topic));
        List<String> modeTags = switch (mode) {
            case "dance_first_video" -> List.of("舞蹈账号", "第一条视频", "起号", "开场动作", "镜头节奏");
            case "opening_hook" -> List.of("前三秒", "开场设计", "留人", "推荐机制", "镜头节奏");
            case "series_plan" -> List.of("系列内容", "账号规划", "内容节奏", "起号", "更新策略");
            case "first_video" -> List.of("第一条视频", "起号", "内容切口", "新号运营", "结构设计");
            default -> List.of("内容策划", "选题", "视频脚本", "创作灵感", "账号运营");
        };
        List<String> items = new ArrayList<>();
        items.add(subject);
        items.addAll(TextUtils.extractKeywords(topic, 6));
        items.addAll(modeTags);
        items.add("B站创作");
        items.add(style);
        List<String> tags = new ArrayList<>();
        for (String item : items) {
            String clean = cleanText(item);
            if (clean.length() >= 2 && !tags.contains(clean)) {
                tags.add(clean);
            }
            if (tags.size() >= 12) {
                break;
            }
        }
        return tags;
    }

    private String buildPinnedComment(String topic) {
        String mode = topicMode(topic);
        String subject = contentSubject(extractSubject(topic));
        if ("dance_first_video".equals(mode)) {
            return "你觉得做 " + subject + "，第一条应该先试卡点、轻剧情还是简单动作？评论区留一个，我继续往下拆。";
        }
        if ("opening_hook".equals(mode)) {
            return "你现在最卡的是开场镜头、动作选择，还是结尾互动？评论区留一个点，我下一条继续补。";
        }
        if ("series_plan".equals(mode)) {
            return "你现在最卡的是第 1 条、第 2 条还是第 3 条？评论区留数字，我按这个顺序继续拆。";
        }
        return "如果你也在做 " + subject + "，评论区告诉我你最想先优化哪一步，我继续按这个方向出下一条。";
    }

    private List<String> pickTitles(JsonNode data, CopywritingResult fallback) {
        if (!JsonUtils.has(data, "titles") || !data.get("titles").isArray()) {
            return fallback.getTitles();
        }
        List<String> titles = new ArrayList<>();
        for (JsonNode item : data.get("titles")) {
            String clean = cleanText(item.asText(""));
            if (!clean.isBlank()) {
                titles.add(clean);
            }
            if (titles.size() >= 3) {
                break;
            }
        }
        return titles.isEmpty() ? fallback.getTitles() : titles;
    }

    private List<Map<String, String>> pickScript(JsonNode data, CopywritingResult fallback) {
        if (!JsonUtils.has(data, "script") || !data.get("script").isArray()) {
            return fallback.getScript();
        }
        List<Map<String, String>> script = new ArrayList<>();
        for (JsonNode item : data.get("script")) {
            if (!item.isObject()) {
                continue;
            }
            String content = cleanText(JsonUtils.text(item, "content"));
            if (content.isBlank()) {
                continue;
            }
            script.add(CopywritingResult.scriptItem(
                    cleanText(JsonUtils.text(item, "section")).isBlank() ? "片段" : cleanText(JsonUtils.text(item, "section")),
                    cleanText(JsonUtils.text(item, "duration")),
                    content
            ));
        }
        return script.size() < 4 ? fallback.getScript() : script;
    }

    private List<String> pickTags(JsonNode data, CopywritingResult fallback) {
        if (!JsonUtils.has(data, "tags") || !data.get("tags").isArray()) {
            return fallback.getTags();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : data.get("tags")) {
            String clean = cleanText(item.asText(""));
            if (clean.length() >= 2 && !result.contains(clean)) {
                result.add(clean);
            }
            if (result.size() >= 15) {
                break;
            }
        }
        return result.isEmpty() ? fallback.getTags() : result;
    }

    private String topicMode(String topic) {
        String cleaned = cleanText(topic);
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

    private String extractSubject(String topic) {
        String cleaned = cleanText(topic);
        if (cleaned.isBlank()) {
            return "这类内容";
        }
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
                return cleanText(cleaned.substring(0, index));
            }
        }
        int split = cleaned.indexOf("：");
        if (split > 0) {
            return cleanText(cleaned.substring(0, split));
        }
        return cleaned;
    }

    private String accountSubject(String subject) {
        String cleaned = cleanText(subject);
        if (cleaned.isBlank() || "这类内容".equals(cleaned)) {
            return "这类账号";
        }
        return cleaned.endsWith("账号") ? cleaned : cleaned + "账号";
    }

    private String contentSubject(String subject) {
        String cleaned = cleanText(subject);
        if (cleaned.isBlank()) {
            return "这类内容";
        }
        return cleaned.endsWith("账号") ? cleaned.substring(0, cleaned.length() - 2) : cleaned;
    }
}
