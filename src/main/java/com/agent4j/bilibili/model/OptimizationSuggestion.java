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

    public OptimizationSuggestion() {
    }

    public String getBvId() {
        return bvId;
    }

    public void setBvId(String bvId) {
        this.bvId = bvId;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public List<String> getOptimizedTitles() {
        return optimizedTitles;
    }

    public void setOptimizedTitles(List<String> optimizedTitles) {
        this.optimizedTitles = optimizedTitles;
    }

    public String getCoverSuggestion() {
        return coverSuggestion;
    }

    public void setCoverSuggestion(String coverSuggestion) {
        this.coverSuggestion = coverSuggestion;
    }

    public List<String> getContentSuggestions() {
        return contentSuggestions;
    }

    public void setContentSuggestions(List<String> contentSuggestions) {
        this.contentSuggestions = contentSuggestions;
    }

    public String getBenchmarkSummary() {
        return benchmarkSummary;
    }

    public void setBenchmarkSummary(String benchmarkSummary) {
        this.benchmarkSummary = benchmarkSummary;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
