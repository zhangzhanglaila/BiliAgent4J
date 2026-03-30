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

    /**
     * 创建视频指标对象。
     */
    public VideoMetrics() {
    }

    /**
     * 获取视频 BV 号。
     *
     * @return BV 号
     */
    public String getBvid() {
        return bvid;
    }

    /**
     * 设置视频 BV 号。
     *
     * @param bvid BV 号
     */
    public void setBvid(String bvid) {
        this.bvid = bvid;
    }

    /**
     * 获取视频标题。
     *
     * @return 标题内容
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置视频标题。
     *
     * @param title 标题内容
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取作者名称。
     *
     * @return UP 主名称
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 设置作者名称。
     *
     * @param author UP 主名称
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * 获取封面地址。
     *
     * @return 封面链接
     */
    public String getCover() {
        return cover;
    }

    /**
     * 设置封面地址。
     *
     * @param cover 封面链接
     */
    public void setCover(String cover) {
        this.cover = cover;
    }

    /**
     * 获取作者 MID。
     *
     * @return UP 主 MID
     */
    public int getMid() {
        return mid;
    }

    /**
     * 设置作者 MID。
     *
     * @param mid UP 主 MID
     */
    public void setMid(int mid) {
        this.mid = mid;
    }

    /**
     * 获取播放量。
     *
     * @return 播放次数
     */
    public int getView() {
        return view;
    }

    /**
     * 设置播放量。
     *
     * @param view 播放次数
     */
    public void setView(int view) {
        this.view = view;
    }

    /**
     * 获取点赞量。
     *
     * @return 点赞次数
     */
    public int getLike() {
        return like;
    }

    /**
     * 设置点赞量。
     *
     * @param like 点赞次数
     */
    public void setLike(int like) {
        this.like = like;
    }

    /**
     * 获取投币量。
     *
     * @return 投币次数
     */
    public int getCoin() {
        return coin;
    }

    /**
     * 设置投币量。
     *
     * @param coin 投币次数
     */
    public void setCoin(int coin) {
        this.coin = coin;
    }

    /**
     * 获取收藏量。
     *
     * @return 收藏次数
     */
    public int getFavorite() {
        return favorite;
    }

    /**
     * 设置收藏量。
     *
     * @param favorite 收藏次数
     */
    public void setFavorite(int favorite) {
        this.favorite = favorite;
    }

    /**
     * 获取评论量。
     *
     * @return 评论次数
     */
    public int getReply() {
        return reply;
    }

    /**
     * 设置评论量。
     *
     * @param reply 评论次数
     */
    public void setReply(int reply) {
        this.reply = reply;
    }

    /**
     * 获取转发量。
     *
     * @return 转发次数
     */
    public int getShare() {
        return share;
    }

    /**
     * 设置转发量。
     *
     * @param share 转发次数
     */
    public void setShare(int share) {
        this.share = share;
    }

    /**
     * 获取视频时长。
     *
     * @return 时长秒数
     */
    public int getDuration() {
        return duration;
    }

    /**
     * 设置视频时长。
     *
     * @param duration 时长秒数
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * 获取平均观看时长。
     *
     * @return 平均观看秒数
     */
    public double getAvgViewDuration() {
        return avgViewDuration;
    }

    /**
     * 设置平均观看时长。
     *
     * @param avgViewDuration 平均观看秒数
     */
    public void setAvgViewDuration(double avgViewDuration) {
        this.avgViewDuration = avgViewDuration;
    }

    /**
     * 获取点赞率。
     *
     * @return 点赞率
     */
    public double getLikeRate() {
        return likeRate;
    }

    /**
     * 设置点赞率。
     *
     * @param likeRate 点赞率
     */
    public void setLikeRate(double likeRate) {
        this.likeRate = likeRate;
    }

    /**
     * 获取完播率。
     *
     * @return 完播率
     */
    public double getCompletionRate() {
        return completionRate;
    }

    /**
     * 设置完播率。
     *
     * @param completionRate 完播率
     */
    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    /**
     * 获取竞争强度分数。
     *
     * @return 竞争分数
     */
    public double getCompetitionScore() {
        return competitionScore;
    }

    /**
     * 设置竞争强度分数。
     *
     * @param competitionScore 竞争分数
     */
    public void setCompetitionScore(double competitionScore) {
        this.competitionScore = competitionScore;
    }

    /**
     * 获取数据来源。
     *
     * @return 来源说明
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置数据来源。
     *
     * @param source 来源说明
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 获取发布时间。
     *
     * @return 发布时间戳
     */
    public long getPubdate() {
        return pubdate;
    }

    /**
     * 设置发布时间。
     *
     * @param pubdate 发布时间戳
     */
    public void setPubdate(long pubdate) {
        this.pubdate = pubdate;
    }

    /**
     * 获取视频链接。
     *
     * @return 视频地址
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置视频链接。
     *
     * @param url 视频地址
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 获取扩展字段。
     *
     * @return 附加数据
     */
    public Map<String, Object> getExtra() {
        return extra;
    }

    /**
     * 设置扩展字段。
     *
     * @param extra 附加数据
     */
    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
