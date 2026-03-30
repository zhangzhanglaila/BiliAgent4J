package com.agent4j.bilibili.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CopywritingResult {

    private String topic;
    private String style;
    private List<String> titles = new ArrayList<>();
    private List<Map<String, String>> script = new ArrayList<>();
    private String description;
    private List<String> tags = new ArrayList<>();
    private String pinnedComment;
    private String rawText = "";

    /**
     * 创建文案生成结果对象。
     */
    public CopywritingResult() {
    }

    /**
     * 构造单条脚本片段。
     *
     * @param section 段落名称
     * @param duration 时长标记
     * @param content 文案内容
     * @return 脚本片段结构
     */
    public static Map<String, String> scriptItem(String section, String duration, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("section", section);
        item.put("duration", duration);
        item.put("content", content);
        return item;
    }

    /**
     * 获取文案选题。
     *
     * @return 选题内容
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 设置文案选题。
     *
     * @param topic 选题内容
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 获取文案风格。
     *
     * @return 风格名称
     */
    public String getStyle() {
        return style;
    }

    /**
     * 设置文案风格。
     *
     * @param style 风格名称
     */
    public void setStyle(String style) {
        this.style = style;
    }

    /**
     * 获取候选标题列表。
     *
     * @return 标题列表
     */
    public List<String> getTitles() {
        return titles;
    }

    /**
     * 设置候选标题列表。
     *
     * @param titles 标题列表
     */
    public void setTitles(List<String> titles) {
        this.titles = titles;
    }

    /**
     * 获取视频脚本内容。
     *
     * @return 脚本片段列表
     */
    public List<Map<String, String>> getScript() {
        return script;
    }

    /**
     * 设置视频脚本内容。
     *
     * @param script 脚本片段列表
     */
    public void setScript(List<Map<String, String>> script) {
        this.script = script;
    }

    /**
     * 获取视频简介。
     *
     * @return 简介内容
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置视频简介。
     *
     * @param description 简介内容
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取标签列表。
     *
     * @return 标签内容
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * 设置标签列表。
     *
     * @param tags 标签内容
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * 获取置顶评论文案。
     *
     * @return 置顶评论内容
     */
    public String getPinnedComment() {
        return pinnedComment;
    }

    /**
     * 设置置顶评论文案。
     *
     * @param pinnedComment 置顶评论内容
     */
    public void setPinnedComment(String pinnedComment) {
        this.pinnedComment = pinnedComment;
    }

    /**
     * 获取原始模型输出文本。
     *
     * @return 原始文本
     */
    public String getRawText() {
        return rawText;
    }

    /**
     * 设置原始模型输出文本。
     *
     * @param rawText 原始文本
     */
    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
