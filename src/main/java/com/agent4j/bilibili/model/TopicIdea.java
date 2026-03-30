package com.agent4j.bilibili.model;

import java.util.ArrayList;
import java.util.List;

public class TopicIdea {

    private String topic;
    private String reason;
    private String videoType;
    private List<String> keywords = new ArrayList<>();
    private double score;

    /**
     * 创建空的选题创意对象。
     */
    public TopicIdea() {
    }

    /**
     * 创建完整的选题创意对象。
     *
     * @param topic 选题名称
     * @param reason 入选理由
     * @param videoType 视频形式
     * @param keywords 关键词列表
     * @param score 评分
     */
    public TopicIdea(String topic, String reason, String videoType, List<String> keywords, double score) {
        this.topic = topic;
        this.reason = reason;
        this.videoType = videoType;
        this.keywords = keywords;
        this.score = score;
    }

    /**
     * 获取选题名称。
     *
     * @return 选题内容
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 设置选题名称。
     *
     * @param topic 选题内容
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 获取选题理由。
     *
     * @return 推荐理由
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置选题理由。
     *
     * @param reason 推荐理由
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * 获取视频形式。
     *
     * @return 视频类型
     */
    public String getVideoType() {
        return videoType;
    }

    /**
     * 设置视频形式。
     *
     * @param videoType 视频类型
     */
    public void setVideoType(String videoType) {
        this.videoType = videoType;
    }

    /**
     * 获取关键词列表。
     *
     * @return 关键词内容
     */
    public List<String> getKeywords() {
        return keywords;
    }

    /**
     * 设置关键词列表。
     *
     * @param keywords 关键词内容
     */
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    /**
     * 获取选题评分。
     *
     * @return 评分结果
     */
    public double getScore() {
        return score;
    }

    /**
     * 设置选题评分。
     *
     * @param score 评分结果
     */
    public void setScore(double score) {
        this.score = score;
    }
}
