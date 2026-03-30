package com.agent4j.bilibili.model;

import java.util.ArrayList;
import java.util.List;

public class OptimizationSuggestion {

    private String bvId;
    private String diagnosis;
    private List<String> optimizedTitles = new ArrayList<>();
    private String coverSuggestion;
    private List<String> contentSuggestions = new ArrayList<>();
    private String benchmarkSummary;
    private String rawText = "";

    /**
     * 创建视频优化建议对象。
     */
    public OptimizationSuggestion() {
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
     * 获取问题诊断。
     *
     * @return 诊断结论
     */
    public String getDiagnosis() {
        return diagnosis;
    }

    /**
     * 设置问题诊断。
     *
     * @param diagnosis 诊断结论
     */
    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    /**
     * 获取优化后的标题列表。
     *
     * @return 标题建议
     */
    public List<String> getOptimizedTitles() {
        return optimizedTitles;
    }

    /**
     * 设置优化后的标题列表。
     *
     * @param optimizedTitles 标题建议
     */
    public void setOptimizedTitles(List<String> optimizedTitles) {
        this.optimizedTitles = optimizedTitles;
    }

    /**
     * 获取封面优化建议。
     *
     * @return 封面建议
     */
    public String getCoverSuggestion() {
        return coverSuggestion;
    }

    /**
     * 设置封面优化建议。
     *
     * @param coverSuggestion 封面建议
     */
    public void setCoverSuggestion(String coverSuggestion) {
        this.coverSuggestion = coverSuggestion;
    }

    /**
     * 获取内容优化建议列表。
     *
     * @return 内容建议
     */
    public List<String> getContentSuggestions() {
        return contentSuggestions;
    }

    /**
     * 设置内容优化建议列表。
     *
     * @param contentSuggestions 内容建议
     */
    public void setContentSuggestions(List<String> contentSuggestions) {
        this.contentSuggestions = contentSuggestions;
    }

    /**
     * 获取对标视频总结。
     *
     * @return 对标分析摘要
     */
    public String getBenchmarkSummary() {
        return benchmarkSummary;
    }

    /**
     * 设置对标视频总结。
     *
     * @param benchmarkSummary 对标分析摘要
     */
    public void setBenchmarkSummary(String benchmarkSummary) {
        this.benchmarkSummary = benchmarkSummary;
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
