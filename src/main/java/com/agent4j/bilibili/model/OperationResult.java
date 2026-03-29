package com.agent4j.bilibili.model;

import java.util.ArrayList;
import java.util.List;

public class OperationResult {

    private String bvId;
    private List<InteractionAction> replies = new ArrayList<>();
    private List<InteractionAction> deletions = new ArrayList<>();
    private List<InteractionAction> likes = new ArrayList<>();
    private List<InteractionAction> follows = new ArrayList<>();
    private String summary;

    public OperationResult() {
    }

    public String getBvId() {
        return bvId;
    }

    public void setBvId(String bvId) {
        this.bvId = bvId;
    }

    public List<InteractionAction> getReplies() {
        return replies;
    }

    public void setReplies(List<InteractionAction> replies) {
        this.replies = replies;
    }

    public List<InteractionAction> getDeletions() {
        return deletions;
    }

    public void setDeletions(List<InteractionAction> deletions) {
        this.deletions = deletions;
    }

    public List<InteractionAction> getLikes() {
        return likes;
    }

    public void setLikes(List<InteractionAction> likes) {
        this.likes = likes;
    }

    public List<InteractionAction> getFollows() {
        return follows;
    }

    public void setFollows(List<InteractionAction> follows) {
        this.follows = follows;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
