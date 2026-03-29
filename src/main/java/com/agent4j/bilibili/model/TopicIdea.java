package com.agent4j.bilibili.model;

import java.util.ArrayList;
import java.util.List;

public class TopicIdea {

    private String topic;
    private String reason;
    private String videoType;
    private List<String> keywords = new ArrayList<>();
    private double score;

    public TopicIdea() {
    }

    public TopicIdea(String topic, String reason, String videoType, List<String> keywords, double score) {
        this.topic = topic;
        this.reason = reason;
        this.videoType = videoType;
        this.keywords = keywords;
        this.score = score;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getVideoType() {
        return videoType;
    }

    public void setVideoType(String videoType) {
        this.videoType = videoType;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
