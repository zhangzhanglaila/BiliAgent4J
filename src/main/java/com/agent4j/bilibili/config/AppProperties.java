package com.agent4j.bilibili.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    public static final Map<String, Integer> PARTITION_TIDS = Map.of(
            "knowledge", 36,
            "tech", 124,
            "life", 160,
            "game", 4,
            "ent", 5
    );

    public static final Map<String, String> PARTITION_ALIASES = Map.ofEntries(
            Map.entry("business", "knowledge"),
            Map.entry("career", "knowledge"),
            Map.entry("study", "knowledge"),
            Map.entry("ai", "tech"),
            Map.entry("digital", "tech"),
            Map.entry("auto", "tech"),
            Map.entry("food", "life"),
            Map.entry("vlog", "life"),
            Map.entry("emotion", "life"),
            Map.entry("fashion", "life"),
            Map.entry("pet", "life"),
            Map.entry("sports", "life"),
            Map.entry("beauty", "ent"),
            Map.entry("dance", "ent"),
            Map.entry("music", "ent"),
            Map.entry("film", "ent"),
            Map.entry("anime", "ent")
    );

    private double requestInterval = 1.2;
    private String dbPath = "bilibili_agents.db";
    private String llmProvider = "openai";
    private String llmApiKey = "";
    private String llmBaseUrl = "https://zapi.aicc0.com/v1";
    private String llmModel = "gpt-5.4";
    private int llmTimeoutSeconds = 75;
    private int llmMaxRetries = 2;
    private double llmRetryBackoffSeconds = 1.6;
    private String biliSessdata = "";
    private String biliBiliJct = "";
    private String defaultPartition = "knowledge";
    private String defaultPeerUps = "546195,15263701,777536";

    public double getRequestInterval() {
        return requestInterval;
    }

    public void setRequestInterval(double requestInterval) {
        this.requestInterval = requestInterval;
    }

    public String getDbPath() {
        return dbPath;
    }

    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public int getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }

    public int getLlmMaxRetries() {
        return llmMaxRetries;
    }

    public void setLlmMaxRetries(int llmMaxRetries) {
        this.llmMaxRetries = llmMaxRetries;
    }

    public double getLlmRetryBackoffSeconds() {
        return llmRetryBackoffSeconds;
    }

    public void setLlmRetryBackoffSeconds(double llmRetryBackoffSeconds) {
        this.llmRetryBackoffSeconds = llmRetryBackoffSeconds;
    }

    public String getBiliSessdata() {
        return biliSessdata;
    }

    public void setBiliSessdata(String biliSessdata) {
        this.biliSessdata = biliSessdata;
    }

    public String getBiliBiliJct() {
        return biliBiliJct;
    }

    public void setBiliBiliJct(String biliBiliJct) {
        this.biliBiliJct = biliBiliJct;
    }

    public String getDefaultPartition() {
        return defaultPartition;
    }

    public void setDefaultPartition(String defaultPartition) {
        this.defaultPartition = defaultPartition;
    }

    public String getDefaultPeerUps() {
        return defaultPeerUps;
    }

    public void setDefaultPeerUps(String defaultPeerUps) {
        this.defaultPeerUps = defaultPeerUps;
    }

    public List<Integer> defaultPeerUpIds() {
        if (defaultPeerUps == null || defaultPeerUps.isBlank()) {
            return new ArrayList<>();
        }
        return List.of(defaultPeerUps.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String normalizePartition(String partitionName) {
        String name = partitionName == null || partitionName.isBlank()
                ? defaultPartition
                : partitionName.trim().toLowerCase();
        String normalized = PARTITION_ALIASES.getOrDefault(name, name);
        return PARTITION_TIDS.containsKey(normalized) ? normalized : "knowledge";
    }

    public boolean llmEnabled() {
        return llmApiKey != null && !llmApiKey.isBlank();
    }

    public String runtimeMode() {
        return llmEnabled() ? "llm_agent" : "rules";
    }

    public int partitionTid(String partitionName) {
        return PARTITION_TIDS.get(normalizePartition(partitionName));
    }
}
