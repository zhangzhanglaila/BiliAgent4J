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

    /**
     * 创建互动执行结果对象。
     */
    public OperationResult() {
    }

    /**
     * 获取目标视频 BV 号。
     *
     * @return BV 号
     */
    public String getBvId() {
        return bvId;
    }

    /**
     * 设置目标视频 BV 号。
     *
     * @param bvId BV 号
     */
    public void setBvId(String bvId) {
        this.bvId = bvId;
    }

    /**
     * 获取回复动作列表。
     *
     * @return 回复动作
     */
    public List<InteractionAction> getReplies() {
        return replies;
    }

    /**
     * 设置回复动作列表。
     *
     * @param replies 回复动作
     */
    public void setReplies(List<InteractionAction> replies) {
        this.replies = replies;
    }

    /**
     * 获取删除动作列表。
     *
     * @return 删除动作
     */
    public List<InteractionAction> getDeletions() {
        return deletions;
    }

    /**
     * 设置删除动作列表。
     *
     * @param deletions 删除动作
     */
    public void setDeletions(List<InteractionAction> deletions) {
        this.deletions = deletions;
    }

    /**
     * 获取点赞动作列表。
     *
     * @return 点赞动作
     */
    public List<InteractionAction> getLikes() {
        return likes;
    }

    /**
     * 设置点赞动作列表。
     *
     * @param likes 点赞动作
     */
    public void setLikes(List<InteractionAction> likes) {
        this.likes = likes;
    }

    /**
     * 获取关注动作列表。
     *
     * @return 关注动作
     */
    public List<InteractionAction> getFollows() {
        return follows;
    }

    /**
     * 设置关注动作列表。
     *
     * @param follows 关注动作
     */
    public void setFollows(List<InteractionAction> follows) {
        this.follows = follows;
    }

    /**
     * 获取执行摘要。
     *
     * @return 汇总说明
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 设置执行摘要。
     *
     * @param summary 汇总说明
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }
}
