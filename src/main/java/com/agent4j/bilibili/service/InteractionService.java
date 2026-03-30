package com.agent4j.bilibili.service;

import com.agent4j.bilibili.model.InteractionAction;
import com.agent4j.bilibili.model.OperationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InteractionService {

    private final VideoResolverService videoResolverService;
    private final LlmClientService llmClientService;
    private final BilibiliHttpSupport httpSupport;

    /**
     * 创建互动运营服务。
     *
     * @param videoResolverService 视频解析服务
     * @param llmClientService LLM 客户端服务
     * @param httpSupport B 站请求支持组件
     */
    public InteractionService(VideoResolverService videoResolverService, LlmClientService llmClientService, BilibiliHttpSupport httpSupport) {
        this.videoResolverService = videoResolverService;
        this.llmClientService = llmClientService;
        this.httpSupport = httpSupport;
    }

    /**
     * 分析评论并生成互动运营建议。
     *
     * @param bvId 目标视频 BV 号
     * @param dryRun 是否仅演练
     * @return 互动建议结果
     */
    public OperationResult processVideoInteractions(String bvId, boolean dryRun) {
        List<InteractionAction> replies = new ArrayList<>();
        List<InteractionAction> deletions = new ArrayList<>();
        List<InteractionAction> likes = new ArrayList<>();
        List<InteractionAction> follows = new ArrayList<>();

        List<Map<String, Object>> comments = fetchComments(bvId);
        for (Map<String, Object> item : comments) {
            String message = map(item.get("content")).getOrDefault("message", "").toString();
            Map<String, Object> member = map(item.get("member"));
            String uname = member.getOrDefault("uname", "匿名用户").toString();
            String mid = member.getOrDefault("mid", "0").toString();
            String rpid = String.valueOf(item.getOrDefault("rpid", "unknown"));

            if (isSpam(message)) {
                deletions.add(new InteractionAction("delete_comment", rpid, "识别为垃圾评论，建议删除：" + message, dryRun));
                continue;
            }

            replies.add(new InteractionAction("reply_comment", rpid, "回复 @" + uname + ": " + generateReply(message), dryRun));
            likes.add(new InteractionAction("like_comment", rpid, "为评论点赞：" + message.substring(0, Math.min(message.length(), 20)), dryRun));
            if (message.length() >= 12 || message.contains("有用") || message.contains("收藏") || message.contains("三连")) {
                follows.add(new InteractionAction("follow_user", mid, "建议关注优质用户 @" + uname, dryRun));
            }
        }

        OperationResult result = new OperationResult();
        result.setBvId(bvId);
        result.setReplies(replies);
        result.setDeletions(deletions);
        result.setLikes(likes);
        result.setFollows(follows);
        result.setSummary("共处理 " + comments.size() + " 条互动，建议回复 " + replies.size() + " 条，删除 " + deletions.size() + " 条，点赞 " + likes.size() + " 条，关注 " + follows.size() + " 人。");
        return result;
    }

    /**
     * 获取目标视频的评论数据。
     *
     * @param bvId 目标视频 BV 号
     * @return 评论列表
     */
    private List<Map<String, Object>> fetchComments(String bvId) {
        try {
            Map<String, Object> info = videoResolverService.fetchVideoInfo("https://www.bilibili.com/video/" + bvId, bvId);
            long aid = Long.parseLong(String.valueOf(info.getOrDefault("aid", "0")));
            if (aid <= 0) {
                return demoComments();
            }
            return parseComments(httpSupport.fetchJson("https://api.bilibili.com/x/v2/reply/main?oid=" + aid + "&type=1&mode=3&next=0"));
        } catch (Exception exception) {
            return demoComments();
        }
    }

    /**
     * 解析评论接口返回结果。
     *
     * @param payload 评论接口载荷
     * @return 结构化评论列表
     */
    private List<Map<String, Object>> parseComments(com.fasterxml.jackson.databind.JsonNode payload) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode reply : payload.path("data").path("replies")) {
            result.add(Map.of(
                    "rpid", reply.path("rpid").asText(""),
                    "content", Map.of("message", reply.path("content").path("message").asText("")),
                    "member", Map.of(
                            "uname", reply.path("member").path("uname").asText("匿名用户"),
                            "mid", reply.path("member").path("mid").asText("0")
                    )
            ));
        }
        return result.isEmpty() ? demoComments() : result;
    }

    /**
     * 判断评论是否属于垃圾内容。
     *
     * @param text 评论文本
     * @return 是否命中垃圾词规则
     */
    private boolean isSpam(String text) {
        String lower = text == null ? "" : text.toLowerCase();
        return List.of("兼职", "返利", "私聊", "vx", "微信", "广告", "废物", "引战")
                .stream()
                .anyMatch(lower::contains);
    }

    /**
     * 根据评论内容生成简短回复。
     *
     * @param text 评论文本
     * @return 回复文案
     */
    private String generateReply(String text) {
        String fallback;
        if (text.contains("谢谢") || text.contains("支持") || text.contains("喜欢")) {
            fallback = "感谢支持，后面我会继续更新更实用的内容。";
        } else if (text.contains("?") || text.contains("？") || text.contains("怎么") || text.contains("为什么")) {
            fallback = "这个问题问得很好，我下一期会补充更详细的实操案例。";
        } else {
            fallback = "你这个观点很有代表性，评论区也可以继续展开聊聊。";
        }
        return llmClientService.invokeText(
                "你是 B 站 UP 主助手，回复要自然、真诚、简洁。",
                "评论内容：" + text + "\n回复风格：友好\n请生成一条 30 字以内的中文回复。",
                fallback
        );
    }

    /**
     * 返回演示评论数据。
     *
     * @return 演示评论列表
     */
    private List<Map<String, Object>> demoComments() {
        return List.of(
                Map.of("rpid", "demo-1", "content", Map.of("message", "这个方法挺有用，谢谢"), "member", Map.of("uname", "粉丝A", "mid", 101)),
                Map.of("rpid", "demo-2", "content", Map.of("message", "怎么做到开头留人？"), "member", Map.of("uname", "粉丝B", "mid", 102)),
                Map.of("rpid", "demo-3", "content", Map.of("message", "兼职返利 vx123"), "member", Map.of("uname", "广告号", "mid", 103))
        );
    }

    /**
     * 将对象安全转换为 Map。
     *
     * @param value 原始值
     * @return 转换后的映射结果
     */
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new java.util.LinkedHashMap<>();
    }
}
