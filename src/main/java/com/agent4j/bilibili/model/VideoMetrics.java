package com.agent4j.bilibili.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class VideoMetrics {

    private String bvid;
    private String title;
    private String author = "";
    private String cover = "";
    private int mid;
    private int view;
    private int like;
    private int coin;
    private int favorite;
    private int reply;
    private int share;
    private int duration;
    private double avgViewDuration;
    private double likeRate;
    private double completionRate;
    private double competitionScore;
    private String source = "";
    private long pubdate;
    private String url = "";
    private Map<String, Object> extra = new LinkedHashMap<>();

    public VideoMetrics() {
    }

    public String getBvid() {
        return bvid;
    }

    public void setBvid(String bvid) {
        this.bvid = bvid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public int getMid() {
        return mid;
    }

    public void setMid(int mid) {
        this.mid = mid;
    }

    public int getView() {
        return view;
    }

    public void setView(int view) {
        this.view = view;
    }

    public int getLike() {
        return like;
    }

    public void setLike(int like) {
        this.like = like;
    }

    public int getCoin() {
        return coin;
    }

    public void setCoin(int coin) {
        this.coin = coin;
    }

    public int getFavorite() {
        return favorite;
    }

    public void setFavorite(int favorite) {
        this.favorite = favorite;
    }

    public int getReply() {
        return reply;
    }

    public void setReply(int reply) {
        this.reply = reply;
    }

    public int getShare() {
        return share;
    }

    public void setShare(int share) {
        this.share = share;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public double getAvgViewDuration() {
        return avgViewDuration;
    }

    public void setAvgViewDuration(double avgViewDuration) {
        this.avgViewDuration = avgViewDuration;
    }

    public double getLikeRate() {
        return likeRate;
    }

    public void setLikeRate(double likeRate) {
        this.likeRate = likeRate;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public double getCompetitionScore() {
        return competitionScore;
    }

    public void setCompetitionScore(double competitionScore) {
        this.competitionScore = competitionScore;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getPubdate() {
        return pubdate;
    }

    public void setPubdate(long pubdate) {
        this.pubdate = pubdate;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
