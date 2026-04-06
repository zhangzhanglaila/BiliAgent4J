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
import java.util.Set;
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
            "干货", "如果你想看我继续拆同类题材，评论区留一个方向，我按实战继续出。",
            "教学", "如果你想让我把这个题材拆成拍摄清单，评论区留\"继续\"，我下一条直接给模板。",
            "搞笑", "如果你也踩过这种坑，评论区打一个\"我也这样\"，我继续把后面两条内容排给你。",
            "混剪", "如果你想看我把镜头节奏和卡点顺序继续拆开，评论区留题材，我下一条直接排镜头。"
    );

    private static final Set<String> LIFE_SCRIPT_BANNED_TOKENS = Set.of(
            "切口", "测反馈", "反馈", "完播", "方向跑偏",
            "实战拆解", "实战继续出", "推荐机制", "留人",
            "结构", "表达", "运营", "起量", "账号", "流量",
            "停下来看", "结果导向"
    );

    private static final Set<String> LIFE_RECORD_TOKENS = Set.of(
            "异地恋", "报备", "情侣", "恋爱", "日常", "生活",
            "vlog", "记录", "通勤", "下班", "回家", "碎碎念"
    );

    private static final Set<String> ROMANCE_DAILY_TOKENS = Set.of(
            "异地恋", "情侣", "恋爱", "约会", "见面", "520",
            "女友", "男友", "报备"
    );

    private static final List<String[]> SCENE_MAPPING = List.of(
            new String[]{"酒店", "酒店", "躺酒店", "回酒店"},
            new String[]{"早午餐", "早饭", "早餐", "早午餐", "午饭", "中饭", "自助早饭", "自助中饭"},
            new String[]{"逛街拍照", "逛街", "拍照", "外景拍照", "散步", "压马路"},
            new String[]{"小清吧", "清吧", "小清吧", "小酒馆", "酒吧"},
            new String[]{"外卖", "外卖", "夜宵"}
    );

    private final LlmClientService llmClientService;

    /**
     * 创建文案服务并注入模型依赖
     * @param llmClientService LLM 客户端服务
     */
    public CopywritingService(LlmClientService llmClientService) {
        this.llmClientService = llmClientService;
    }

    /**
     * 根据主题、创意和风格生成完整文案
     * @param topic 目标主题
     * @param topicIdea 选题创意
     * @param style 文案风格
     * @return 标题、脚本、简介和标签结果
     */
    public CopywritingResult run(String topic, TopicIdea topicIdea, String style) {
        String finalTopic = cleanText(topic == null || topic.isBlank()
                ? topicIdea == null ? "B站高效运营" : topicIdea.getTopic()
                : topic);
        String finalStyle = cleanText(style).isBlank() ? "干货" : cleanText(style);

        CopywritingResult fallbackResult = fallback(finalTopic, finalStyle);
        Map<String, Object> fallbackJson = new LinkedHashMap<>();
        fallbackJson.put("titles", fallbackResult.getTitles());
        fallbackJson.put("script", fallbackResult.getScript());
        fallbackJson.put("description", fallbackResult.getDescription());
        fallbackJson.put("tags", fallbackResult.getTags());
        fallbackJson.put("pinned_comment", fallbackResult.getPinnedComment());

        JsonNode data = llmClientService.invokeJson(
                "你是 B 站百万粉 UP 主的文案总监，输出能直接发布的视频文案。",
                "主题：" + finalTopic + "\n"
                        + "风格：" + finalStyle + "\n"
                        + "风格要求：" + STYLE_GUIDE.getOrDefault(finalStyle, STYLE_GUIDE.get("干货")) + "\n"
                        + "请基于创作者场景生成自然、可直接口播和发布的 JSON，避免复述输入词，避免机械使用\"高效做法\"等空泛表达。"
                        + " 字段为：titles(3个标题), script(数组，每项包含 section/duration/content), description, tags(10-15个), pinned_comment。",
                fallbackJson
        );

        CopywritingResult result = new CopywritingResult();
        result.setTopic(finalTopic);
        result.setStyle(finalStyle);
        result.setTitles(pickTitles(data, fallbackResult));
        result.setScript(pickScript(data, fallbackResult));
        result.setDescription(cleanText(JsonUtils.text(data, "description")).isBlank()
                ? fallbackResult.getDescription()
                : cleanText(JsonUtils.text(data, "description")));
        result.setTags(pickTags(data, fallbackResult));
        String pinned = cleanText(JsonUtils.text(data, "pinned_comment"));
        result.setPinnedComment(pinned.isBlank() ? fallbackResult.getPinnedComment() : pinned);
        result.setRawText(data.toString());
        return result;
    }

    /**
     * 清理文案中的空白和异常字符
     * @param text 原始文本
     * @return 清理后的文本
     */
    public String cleanText(String text) {
        return TextUtils.cleanCopyText(text == null ? "" : text);
    }

    /**
     * 在模型不可用时按规则生成文案
     * @param topic 目标主题
     * @param style 文案风格
     * @return 回退文案结果
     */
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

    /**
     * 按主题模式和风格生成标题候选
     * @param topic 目标主题
     * @param style 文案风格
     * @return 标题列表
     */
    private List<String> buildTitles(String topic, String style) {
        String mode = topicMode(topic);
        String subject = extractSubject(topic);
        String accountSubject = accountSubject(subject);
        String contentSubject = contentSubject(topic);
        String titleSubjectVal = titleSubject(topic);

        // 生活记录 / 恋爱日常 vlog 类型
        if (isLifeRecordTopic(topic)) {
            String lifeSubject = !titleSubjectVal.isBlank() ? titleSubjectVal :
                    (!contentSubject.isBlank() && !"这类内容".equals(contentSubject) ? contentSubject : "日常");
            return buildLifeRecordTitles(topic, lifeSubject);
        }

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

    /**
     * 按主题模式和风格生成分段脚本
     * @param topic 目标主题
     * @param style 文案风格
     * @return 脚本分段列表
     */
    private List<Map<String, String>> buildScript(String topic, String style) {
        String mode = topicMode(topic);
        String subject = extractSubject(topic);
        String accountSubject = accountSubject(subject);
        String contentSubject = contentSubject(topic);
        String ending = STYLE_ENDING.getOrDefault(style, STYLE_ENDING.get("干货"));

        // 恋爱日常 vlog 类型
        if (isRomanceDailyTopic(topic)) {
            return buildRomanceDailyScript(topic);
        }

        if ("dance_first_video".equals(mode)) {
            return List.of(
                    CopywritingResult.scriptItem("开头钩子", "0-8 秒", "如果你正准备做" + accountSubject + "，第一条别急着上完整编舞。先选 3 秒内能看懂、动作识别度高、镜头能立住的内容，更容易拿到第一波推荐。"),
                    CopywritingResult.scriptItem("动作选择", "8-28 秒", "优先拍节奏明确、上手不难、能带表情管理的动作。太难的编排会拖慢更新，也不利于你快速测出观众偏好。"),
                    CopywritingResult.scriptItem("镜头节奏", "28-58 秒", "开头先给最抓眼的定格或转身，中段补一个近景表情点，结尾留一句互动提问，比如\"下一条想看我跳哪种风格\"。"),
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
                    CopywritingResult.scriptItem("第 1 条作用", "8-26 秒", "第一条负责让观众记住你的人设和最强记忆点，内容要简单、明确，好理解，不要先堆复杂信息。"),
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

    /**
     * 生成视频简介文案
     * @param topic 目标主题
     * @param style 文案风格
     * @return 视频简介
     */
    private String buildDescription(String topic, String style) {
        // 恋爱日常 vlog 类型
        if (isRomanceDailyTopic(topic)) {
            return "这条就想把\"" + topic + "\"里的见面日常慢慢记下来。"
                    + "酒店、早午餐、逛街拍照和小清吧都不是多特别的行程，但放在异地恋见面的那天里，每一段都会变得很舍不得。";
        }
        String mode = topicMode(topic);
        String subject = contentSubject(extractSubject(topic));
        Map<String, String> summaries = Map.of(
                "dance_first_video", "重点拆第一条起号该拍什么、动作怎么选、镜头顺序怎么排。",
                "opening_hook", "重点拆前三秒怎么留人、开场动作怎么设计、节奏怎么推进。",
                "series_plan", "重点拆前三条内容怎么分工，避免系列内容一上来就散。",
                "first_video", "重点拆第一条视频的切口、结构和后续承接方式。",
                "general", "重点拆选题切口、表达结构和互动设计。"
        );
        return "本条围绕\"" + topic + "\"展开，适合正在做 " + subject + " 的创作者参考。"
                + summaries.getOrDefault(mode, summaries.get("general"))
                + " 文案风格是\"" + style + "\"可直接按段落改成自己的版本。";
    }

    /**
     * 提取主题关键词并生成标签列表
     * @param topic 目标主题
     * @param style 文案风格
     * @return 标签列表
     */
    private List<String> buildTags(String topic, String style) {
        // 恋爱日常 vlog 类型
        if (isRomanceDailyTopic(topic)) {
            List<String> items = new ArrayList<>();
            items.add("异地恋");
            items.add("情侣日常");
            items.add("约会vlog");
            items.add("见面日常");
            items.addAll(extractLifeScenes(topic));
            items.addAll(TextUtils.extractKeywords(topic, 6));
            items.add("B站创作");
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

    /**
     * 生成引导互动的置顶评论
     * @param topic 目标主题
     * @return 置顶评论文案
     */
    private String buildPinnedComment(String topic) {
        // 恋爱日常 vlog 类型
        if (isRomanceDailyTopic(topic)) {
            if (topic.contains("异地恋")) {
                return "异地恋见面的哪一个瞬间最让你破防？是刚见面、一起吃饭，还是晚上准备分开的时候？";
            }
            return "如果是你，你会把这一天里最舍不得结束的那一段留在哪个时刻？评论区聊聊。";
        }
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

    /**
     * 从模型结果中提取标题并规范化
     * @param data 模型返回数据
     * @param fallback 回退文案结果
     * @return 标题列表
     */
    private List<String> pickTitles(JsonNode data, CopywritingResult fallback) {
        List<String> rawTitles = new ArrayList<>();
        if (JsonUtils.has(data, "titles") && data.get("titles").isArray()) {
            for (JsonNode item : data.get("titles")) {
                String clean = cleanText(item.asText(""));
                if (!clean.isBlank()) {
                    rawTitles.add(clean);
                }
            }
        }
        if (rawTitles.isEmpty()) {
            return fallback.getTitles();
        }
        return normalizeTitles(rawTitles, fallback.getTitles());
    }

    /**
     * 从模型结果中提取脚本并回退默认值
     * @param data 模型返回数据
     * @param fallback 回退文案结果
     * @return 脚本分段列表
     */
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
            String section = cleanText(JsonUtils.text(item, "section"));
            script.add(CopywritingResult.scriptItem(
                    section.isBlank() ? "片段" : section,
                    cleanText(JsonUtils.text(item, "duration")),
                    content
            ));
        }
        if (script.isEmpty()) {
            return fallback.getScript();
        }
        if (script.size() < 4) {
            return fallback.getScript();
        }
        if (isRomanceDailyTopic(fallback.getTopic()) && !isValidRomanceDailyScript(script)) {
            return fallback.getScript();
        }
        return script;
    }

    /**
     * 从模型结果中提取标签并回退默认值
     * @param data 模型返回数据
     * @param fallback 回退文案结果
     * @return 标签列表
     */
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

    /**
     * 识别选题所属的表达模式
     * @param topic 目标主题
     * @return 主题模式标识
     */
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

    /**
     * 从选题文案中提取核心主体
     * @param topic 目标主题
     * @return 主体文本
     */
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

    /**
     * 将主体整理为账号描述
     * @param subject 核心主体
     * @return 账号主体描述
     */
    private String accountSubject(String subject) {
        String cleaned = cleanText(subject);
        if (cleaned.isBlank() || "这类内容".equals(cleaned)) {
            return "这类账号";
        }
        return cleaned.endsWith("账号") ? cleaned : cleaned + "账号";
    }

    /**
     * 将主体整理为内容描述
     * @param subject 核心主体
     * @return 内容主体描述
     */
    private String contentSubject(String subject) {
        String cleaned = cleanText(subject);
        if (cleaned.isBlank()) {
            return "这类内容";
        }
        return cleaned.endsWith("账号") ? cleaned.substring(0, cleaned.length() - 2) : cleaned;
    }

    /**
     * 判断主题是否更接近日常记录 / 生活区 vlog 的表达场景
     * @param topic 目标主题
     * @return 是否为生活记录类主题
     */
    private boolean isLifeRecordTopic(String topic) {
        String text = (cleanText(topic) + " " + titleSubject(topic)).toLowerCase(Locale.ROOT);
        for (String token : LIFE_RECORD_TOKENS) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断主题是否更明确属于恋爱 / 约会 / 异地恋日常口播场景
     * @param topic 目标主题
     * @return 是否为恋爱日常类主题
     */
    private boolean isRomanceDailyTopic(String topic) {
        String text = cleanText(topic).toLowerCase(Locale.ROOT);
        for (String token : ROMANCE_DAILY_TOKENS) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断标题是否仍然模板化或提问式
     * @param title 标题
     * @return 是否为劣质标题
     */
    private boolean isBadTitle(String title) {
        String clean = cleanText(title);
        if (clean.isBlank()) {
            return true;
        }
        if (clean.contains("?") || clean.contains("？") || clean.endsWith("吗") || clean.endsWith("呢")) {
            return true;
        }
        if (clean.startsWith("别直接") || clean.startsWith("别一上来就")) {
            return true;
        }
        String[] badTokens = {"为什么", "如何", "怎么", "哪种", "哪类", "该怎么", "先做什么", "先拍什么",
                "先跳什么", "更容易起量", "更容易进推荐", "更容易被推荐",
                "先做哪种切口", "教程", "攻略", "教你"};
        for (String token : badTokens) {
            if (clean.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一清洗标题列表，劣质标题自动过滤
     * @param rawTitles 原始标题列表
     * @param fallbackTitles 兜底标题列表
     * @return 清洗后的标题列表
     */
    private List<String> normalizeTitles(List<String> rawTitles, List<String> fallbackTitles) {
        List<String> result = new ArrayList<>();
        for (String item : rawTitles) {
            String clean = cleanText(item);
            if (isBadTitle(clean) || result.contains(clean)) {
                continue;
            }
            result.add(clean);
        }
        for (String item : fallbackTitles) {
            String clean = cleanText(item);
            if (clean.isBlank() || result.contains(clean)) {
                continue;
            }
            result.add(clean);
            if (result.size() >= 3) {
                break;
            }
        }
        return result.size() >= 3 ? result.subList(0, 3) : fallbackTitles;
    }

    /**
     * 从标题里抽取生活场景关键词
     * @param topic 目标主题
     * @return 生活场景列表
     */
    private List<String> extractLifeScenes(String topic) {
        String clean = cleanText(topic);
        int colonIdx = clean.indexOf("：");
        String detail = colonIdx > 0 ? clean.substring(colonIdx + 1) : clean;
        String[] parts = detail.split("[+＋|｜/、，,]");
        List<String> scenes = new ArrayList<>();
        for (String part : parts) {
            String value = cleanText(part);
            if (value.length() >= 2) {
                scenes.add(value);
            }
        }
        List<String> merged = new ArrayList<>();
        for (String[] mapping : SCENE_MAPPING) {
            String normalized = mapping[0];
            for (String scene : scenes) {
                for (int i = 1; i < mapping.length; i++) {
                    if (scene.contains(mapping[i]) && !merged.contains(normalized)) {
                        merged.add(normalized);
                        break;
                    }
                }
            }
        }
        for (String defaultScene : List.of("酒店", "早午餐", "逛街拍照", "小清吧")) {
            if (!merged.contains(defaultScene)) {
                merged.add(defaultScene);
            }
            if (merged.size() >= 5) {
                break;
            }
        }
        return merged.subList(0, Math.min(5, merged.size()));
    }

    /**
     * 为恋爱日常题材生成结尾互动文案
     * @param topic 目标主题
     * @param scenes 生活场景列表
     * @return 结尾互动文案
     */
    private String buildLifeRecordInteraction(String topic, List<String> scenes) {
        if (topic.contains("异地恋")) {
            return "异地恋见面的时候，你们最舍不得结束的是哪一段？评论区告诉我，我想看看是不是大家都一样。";
        }
        if (topic.contains("情侣") || topic.contains("约会")) {
            String lastScene = scenes.size() > 3 ? scenes.get(3) : scenes.isEmpty() ? "晚上" : scenes.get(scenes.size() - 1);
            return "如果是你，你会把这天里最想反复重来的那一段留给" + lastScene + "吗？评论区聊聊。";
        }
        return "如果是你，你会把今天最想反复过一遍的那一段留在哪个时刻？评论区告诉我。";
    }

    /**
     * 为恋爱 / 异地恋 / 约会 vlog 生成可直接口播的 4 段脚本
     * @param topic 目标主题
     * @return 脚本分段列表
     */
    private List<Map<String, String>> buildRomanceDailyScript(String topic) {
        List<String> scenes = extractLifeScenes(topic);
        String firstScene = scenes.isEmpty() ? "酒店" : scenes.get(0);
        String secondScene = scenes.size() > 1 ? scenes.get(1) : "早午餐";
        String thirdScene = scenes.size() > 2 ? scenes.get(2) : "逛街拍照";
        String lastScene = scenes.size() > 3 ? scenes.get(3) : scenes.isEmpty() ? "小清吧" : scenes.get(scenes.size() - 1);
        String meetingText = topic.contains("异地恋") ? "异地恋见面" : "情侣见面";
        String interaction = buildLifeRecordInteraction(topic, scenes);

        return List.of(
                CopywritingResult.scriptItem("开头钩子", "0-8 秒", meetingText + "最戳人的，真的不是多隆重，就是一醒来发现对方就在旁边，连赖在" + firstScene + "里发呆都觉得很甜。"),
                CopywritingResult.scriptItem("核心画面 1", "8-28 秒", "我们慢慢出门去吃" + secondScene + "，一边挑吃的，一边说昨晚没说完的小事，那种终于能面对面聊天的感觉，一下子就把距离感冲淡了。"),
                CopywritingResult.scriptItem("核心画面 2", "28-56 秒", "后面又去" + thirdScene + "，风有点大，人也冻得直缩脖子，但他还是会一边帮我看镜头，一边催我把手揣回口袋。到了晚上坐进" + lastScene + "，整个人才真的慢下来，突然就很想把这一天按暂停。"),
                CopywritingResult.scriptItem("结尾互动", "56-75 秒", interaction)
        );
    }

    /**
     * 校验恋爱日常脚本是否真的像短视频口播
     * @param script 脚本分段列表
     * @return 是否为有效脚本
     */
    private boolean isValidRomanceDailyScript(List<Map<String, String>> script) {
        if (script.size() < 4) {
            return false;
        }
        List<String> expectedSections = List.of("开头钩子", "核心画面 1", "核心画面 2", "结尾互动");
        for (int i = 0; i < expectedSections.size(); i++) {
            String current = cleanText(script.get(i).getOrDefault("section", ""));
            if (!current.equals(expectedSections.get(i))) {
                return false;
            }
        }
        StringBuilder fullText = new StringBuilder();
        for (Map<String, String> item : script) {
            fullText.append(cleanText(item.getOrDefault("content", ""))).append(" ");
        }
        String text = fullText.toString();
        for (String token : LIFE_SCRIPT_BANNED_TOKENS) {
            if (text.contains(token)) {
                return false;
            }
        }
        List<String> sceneTokens = List.of("酒店", "早饭", "早餐", "早午餐", "午饭", "中饭", "逛街", "拍照", "清吧", "见面");
        boolean hasScene = false;
        for (String token : sceneTokens) {
            if (text.contains(token)) {
                hasScene = true;
                break;
            }
        }
        if (!hasScene) {
            return false;
        }
        List<String> emotionTokens = List.of("甜", "想", "舍不得", "终于", "慢下来", "发呆", "聊天", "抱", "开心", "想念");
        for (String token : emotionTokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成更像生活区 vlog 的日常记录标题
     * @param topic 目标主题
     * @param subject 标题主体
     * @return 生活记录标题列表
     */
    private List<String> buildLifeRecordTitles(String topic, String subject) {
        String text = (cleanText(topic) + " " + subject).toLowerCase(Locale.ROOT);
        if (text.contains("异地恋") && text.contains("报备")) {
            return List.of(
                    "异地恋报备日常，从早安到晚安都想慢慢告诉你",
                    "今天也在认真报备，吃饭下班回家路上都没落下",
                    "把异地恋过成普通日常，琐碎小事也想第一时间分享"
            );
        }
        if (text.contains("异地恋")) {
            return List.of(
                    "异地恋的一天，从早安电话到晚安视频都记下来了",
                    "隔着屏幕过日常，今天的碎碎念也想慢慢分享",
                    "异地恋日常存档，吃饭下班回家路上都在认真联系"
            );
        }
        if (text.contains("报备")) {
            return List.of(
                    "今天也在认真报备，把一天里的小事都慢慢说完",
                    "报备式日常记录，吃饭下班回家路上都想告诉你",
                    "把琐碎日常发给重要的人，这一天也被认真记住了"
            );
        }
        String base = subject;
        if (subject == null || subject.isBlank() || "这类内容".equals(subject)) {
            base = "日常";
        }
        if (base.endsWith("日常")) {
            return List.of(
                    base + "存档，把今天从头到尾慢慢记下来",
                    "围着" + base + "过的一天，琐碎小事也想认真分享",
                    "今天的" + base + "小记录，轻轻松松把状态都留住"
            );
        }
        return List.of(
                base + "日常记录，把今天从头到尾慢慢拍下来",
                "围着" + base + "过的一天，琐碎流程也想认真分享",
                "今天的" + base + "小存档，顺手把真实状态都留住"
        );
    }

    /**
     * 从主题中提取更适合作为标题主语的主体部分
     * @param topic 目标主题
     * @return 标题主体文本
     */
    private String titleSubject(String topic) {
        String base = contentSubject(extractSubject(topic));
        if (base == null || base.isBlank() || "这类内容".equals(base)) {
            base = cleanText(topic);
        }
        String[] patterns = {"^第一条(?:视频)?", "^第[1一二三123]+条", "做成系列内容时", "做系列内容时",
                "别一上来就", "先(?:做|拍|跳)什么", "更容易(?:起量|进推荐|被推荐)",
                "怎么(?:拍|做|设计)?", "如何", "为什么", "起号", "切口", "开场动作", "前三秒",
                "镜头顺序", "内容顺序", "结构设计", "教程", "攻略"};
        for (String pattern : patterns) {
            base = base.replaceAll(pattern, " ");
        }
        base = base.replaceAll("(视频|内容|账号)$", "");
        base = cleanText(base);
        if ("这类".equals(base) || "这类内容".equals(base)) {
            return "";
        }
        return base;
    }
}
