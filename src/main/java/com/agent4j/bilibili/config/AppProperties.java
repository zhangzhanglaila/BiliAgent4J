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
    private String llmReasoningEffort = "";
    private boolean llmDisableResponseStorage;
    private boolean langsmithTracing;
    private String langsmithApiKey = "";
    private String langsmithProject = "bilibili-hot-rag";
    private String langsmithEndpoint = "";
    private int llmTimeoutSeconds = 75;
    private int llmMaxRetries = 2;
    private double llmRetryBackoffSeconds = 1.6;
    private String serpapiApiKey = "";
    private String tavilyApiKey = "";
    private String langchainApiKey = "";
    private String langchainEndpoint = "";
    private String langchainProject = "bilibili-hot-rag";
    private boolean langchainCallbacksBackground = true;
    private String langchainVerbosity = "";
    private String vectorDbPath = "./vector_db";
    private String embeddingModelName = "BAAI/bge-small-zh-v1.5";
    private String embeddingCacheDir = "./model_cache";
    private String embeddingServiceUrl = "http://localhost:8001";
    private String chromaHost = "localhost";
    private int chromaPort = 8000;
    private String biliSessdata = "";
    private String biliBiliJct = "";
    private String defaultPartition = "knowledge";
    private String defaultPeerUps = "546195,15263701,777536";

    /**
     * 获取请求间隔时间。
     *
     * @return 请求间隔秒数
     */
    public double getRequestInterval() {
        return requestInterval;
    }

    /**
     * 设置请求间隔时间。
     *
     * @param requestInterval 请求间隔秒数
     */
    public void setRequestInterval(double requestInterval) {
        this.requestInterval = requestInterval;
    }

    /**
     * 获取数据库文件路径。
     *
     * @return 数据库路径
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * 设置数据库文件路径。
     *
     * @param dbPath 数据库路径
     */
    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * 获取 LLM 服务提供方。
     *
     * @return 提供方名称
     */
    public String getLlmProvider() {
        return llmProvider;
    }

    /**
     * 设置 LLM 服务提供方。
     *
     * @param llmProvider 提供方名称
     */
    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    /**
     * 获取 LLM API Key。
     *
     * @return 接口密钥
     */
    public String getLlmApiKey() {
        return llmApiKey;
    }

    /**
     * 设置 LLM API Key。
     *
     * @param llmApiKey 接口密钥
     */
    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    /**
     * 获取 LLM 接口地址。
     *
     * @return 接口基地址
     */
    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    /**
     * 设置 LLM 接口地址。
     *
     * @param llmBaseUrl 接口基地址
     */
    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    /**
     * 获取 LLM 模型名称。
     *
     * @return 模型标识
     */
    public String getLlmModel() {
        return llmModel;
    }

    /**
     * 设置 LLM 模型名称。
     *
     * @param llmModel 模型标识
     */
    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    /**
     * 获取 LLM 推理努力级别。
     *
     * @return 推理努力配置
     */
    public String getLlmReasoningEffort() {
        return llmReasoningEffort;
    }

    /**
     * 设置 LLM 推理努力级别。
     *
     * @param llmReasoningEffort 推理努力配置
     */
    public void setLlmReasoningEffort(String llmReasoningEffort) {
        this.llmReasoningEffort = llmReasoningEffort;
    }

    /**
     * 判断是否禁用 LLM 响应存储。
     *
     * @return 是否禁用
     */
    public boolean isLlmDisableResponseStorage() {
        return llmDisableResponseStorage;
    }

    /**
     * 设置是否禁用 LLM 响应存储。
     *
     * @param llmDisableResponseStorage 是否禁用
     */
    public void setLlmDisableResponseStorage(boolean llmDisableResponseStorage) {
        this.llmDisableResponseStorage = llmDisableResponseStorage;
    }

    /**
     * 判断是否启用 LangSmith 追踪。
     *
     * @return 是否启用
     */
    public boolean isLangsmithTracing() {
        return langsmithTracing;
    }

    /**
     * 设置是否启用 LangSmith 追踪。
     *
     * @param langsmithTracing 是否启用
     */
    public void setLangsmithTracing(boolean langsmithTracing) {
        this.langsmithTracing = langsmithTracing;
    }

    /**
     * 获取 LangSmith API Key。
     *
     * @return API Key
     */
    public String getLangsmithApiKey() {
        return langsmithApiKey;
    }

    /**
     * 设置 LangSmith API Key。
     *
     * @param langsmithApiKey API Key
     */
    public void setLangsmithApiKey(String langsmithApiKey) {
        this.langsmithApiKey = langsmithApiKey;
    }

    /**
     * 获取 LangSmith 项目名称。
     *
     * @return 项目名称
     */
    public String getLangsmithProject() {
        return langsmithProject;
    }

    /**
     * 设置 LangSmith 项目名称。
     *
     * @param langsmithProject 项目名称
     */
    public void setLangsmithProject(String langsmithProject) {
        this.langsmithProject = langsmithProject;
    }

    /**
     * 获取 LangSmith 端点地址。
     *
     * @return 端点地址
     */
    public String getLangsmithEndpoint() {
        return langsmithEndpoint;
    }

    /**
     * 设置 LangSmith 端点地址。
     *
     * @param langsmithEndpoint 端点地址
     */
    public void setLangsmithEndpoint(String langsmithEndpoint) {
        this.langsmithEndpoint = langsmithEndpoint;
    }

    /**
     * 获取 LLM 超时时间。
     *
     * @return 超时秒数
     */
    public int getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    /**
     * 设置 LLM 超时时间。
     *
     * @param llmTimeoutSeconds 超时秒数
     */
    public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }

    /**
     * 获取 LLM 最大重试次数。
     *
     * @return 重试次数
     */
    public int getLlmMaxRetries() {
        return llmMaxRetries;
    }

    /**
     * 设置 LLM 最大重试次数。
     *
     * @param llmMaxRetries 重试次数
     */
    public void setLlmMaxRetries(int llmMaxRetries) {
        this.llmMaxRetries = llmMaxRetries;
    }

    /**
     * 获取 LLM 重试退避时间。
     *
     * @return 退避秒数
     */
    public double getLlmRetryBackoffSeconds() {
        return llmRetryBackoffSeconds;
    }

    /**
     * 设置 LLM 重试退避时间。
     *
     * @param llmRetryBackoffSeconds 退避秒数
     */
    public void setLlmRetryBackoffSeconds(double llmRetryBackoffSeconds) {
        this.llmRetryBackoffSeconds = llmRetryBackoffSeconds;
    }

    /**
     * 获取 SerpAPI Key。
     *
     * @return API Key
     */
    public String getSerpapiApiKey() {
        return serpapiApiKey;
    }

    /**
     * 设置 SerpAPI Key。
     *
     * @param serpapiApiKey API Key
     */
    public void setSerpapiApiKey(String serpapiApiKey) {
        this.serpapiApiKey = serpapiApiKey;
    }

    /**
     * 获取 Tavily API Key。
     *
     * @return API Key
     */
    public String getTavilyApiKey() {
        return tavilyApiKey;
    }

    /**
     * 设置 Tavily API Key。
     *
     * @param tavilyApiKey API Key
     */
    public void setTavilyApiKey(String tavilyApiKey) {
        this.tavilyApiKey = tavilyApiKey;
    }

    /**
     * 获取 LangChain API Key。
     *
     * @return API Key
     */
    public String getLangchainApiKey() {
        return langchainApiKey;
    }

    /**
     * 设置 LangChain API Key。
     *
     * @param langchainApiKey API Key
     */
    public void setLangchainApiKey(String langchainApiKey) {
        this.langchainApiKey = langchainApiKey;
    }

    /**
     * 获取 LangChain 端点地址。
     *
     * @return 端点地址
     */
    public String getLangchainEndpoint() {
        return langchainEndpoint;
    }

    /**
     * 设置 LangChain 端点地址。
     *
     * @param langchainEndpoint 端点地址
     */
    public void setLangchainEndpoint(String langchainEndpoint) {
        this.langchainEndpoint = langchainEndpoint;
    }

    /**
     * 获取 LangChain 项目名称。
     *
     * @return 项目名称
     */
    public String getLangchainProject() {
        return langchainProject;
    }

    /**
     * 设置 LangChain 项目名称。
     *
     * @param langchainProject 项目名称
     */
    public void setLangchainProject(String langchainProject) {
        this.langchainProject = langchainProject;
    }

    /**
     * 判断是否后台执行 LangChain 回调。
     *
     * @return 是否后台执行
     */
    public boolean isLangchainCallbacksBackground() {
        return langchainCallbacksBackground;
    }

    /**
     * 设置是否后台执行 LangChain 回调。
     *
     * @param langchainCallbacksBackground 是否后台执行
     */
    public void setLangchainCallbacksBackground(boolean langchainCallbacksBackground) {
        this.langchainCallbacksBackground = langchainCallbacksBackground;
    }

    /**
     * 获取 LangChain 日志详细度。
     *
     * @return 日志详细度
     */
    public String getLangchainVerbosity() {
        return langchainVerbosity;
    }

    /**
     * 设置 LangChain 日志详细度。
     *
     * @param langchainVerbosity 日志详细度
     */
    public void setLangchainVerbosity(String langchainVerbosity) {
        this.langchainVerbosity = langchainVerbosity;
    }

    /**
     * 获取向量数据库路径。
     *
     * @return 存储路径
     */
    public String getVectorDbPath() {
        return vectorDbPath;
    }

    /**
     * 设置向量数据库路径。
     *
     * @param vectorDbPath 存储路径
     */
    public void setVectorDbPath(String vectorDbPath) {
        this.vectorDbPath = vectorDbPath;
    }

    /**
     * 获取 Embedding 模型名称。
     *
     * @return 模型名称
     */
    public String getEmbeddingModelName() {
        return embeddingModelName;
    }

    /**
     * 设置 Embedding 模型名称。
     *
     * @param embeddingModelName 模型名称
     */
    public void setEmbeddingModelName(String embeddingModelName) {
        this.embeddingModelName = embeddingModelName;
    }

    /**
     * 获取 Embedding 模型缓存目录。
     *
     * @return 缓存目录
     */
    public String getEmbeddingCacheDir() {
        return embeddingCacheDir;
    }

    /**
     * 设置 Embedding 模型缓存目录。
     *
     * @param embeddingCacheDir 缓存目录
     */
    public void setEmbeddingCacheDir(String embeddingCacheDir) {
        this.embeddingCacheDir = embeddingCacheDir;
    }

    /**
     * 获取 Embedding 服务地址。
     *
     * @return 服务 URL
     */
    public String getEmbeddingServiceUrl() {
        return embeddingServiceUrl;
    }

    /**
     * 设置 Embedding 服务地址。
     *
     * @param embeddingServiceUrl 服务 URL
     */
    public void setEmbeddingServiceUrl(String embeddingServiceUrl) {
        this.embeddingServiceUrl = embeddingServiceUrl;
    }

    /**
     * 获取 Chroma 向量数据库主机地址。
     *
     * @return 主机地址
     */
    public String getChromaHost() {
        return chromaHost;
    }

    /**
     * 设置 Chroma 向量数据库主机地址。
     *
     * @param chromaHost 主机地址
     */
    public void setChromaHost(String chromaHost) {
        this.chromaHost = chromaHost;
    }

    /**
     * 获取 Chroma 向量数据库端口。
     *
     * @return 端口号
     */
    public int getChromaPort() {
        return chromaPort;
    }

    /**
     * 设置 Chroma 向量数据库端口。
     *
     * @param chromaPort 端口号
     */
    public void setChromaPort(int chromaPort) {
        this.chromaPort = chromaPort;
    }

    /**
     * 获取 B 站登录态 SESSDATA。
     *
     * @return SESSDATA 值
     */
    public String getBiliSessdata() {
        return biliSessdata;
    }

    /**
     * 设置 B 站登录态 SESSDATA。
     *
     * @param biliSessdata SESSDATA 值
     */
    public void setBiliSessdata(String biliSessdata) {
        this.biliSessdata = biliSessdata;
    }

    /**
     * 获取 B 站 CSRF Token。
     *
     * @return bili_jct 值
     */
    public String getBiliBiliJct() {
        return biliBiliJct;
    }

    /**
     * 设置 B 站 CSRF Token。
     *
     * @param biliBiliJct bili_jct 值
     */
    public void setBiliBiliJct(String biliBiliJct) {
        this.biliBiliJct = biliBiliJct;
    }

    /**
     * 获取默认分区。
     *
     * @return 默认分区标识
     */
    public String getDefaultPartition() {
        return defaultPartition;
    }

    /**
     * 设置默认分区。
     *
     * @param defaultPartition 默认分区标识
     */
    public void setDefaultPartition(String defaultPartition) {
        this.defaultPartition = defaultPartition;
    }

    /**
     * 获取默认对标 UP 列表。
     *
     * @return 逗号分隔的 UP 主 ID
     */
    public String getDefaultPeerUps() {
        return defaultPeerUps;
    }

    /**
     * 设置默认对标 UP 列表。
     *
     * @param defaultPeerUps 逗号分隔的 UP 主 ID
     */
    public void setDefaultPeerUps(String defaultPeerUps) {
        this.defaultPeerUps = defaultPeerUps;
    }

    /**
     * 解析默认对标 UP 主 ID 列表。
     *
     * @return 对标 UP 主 ID 集合
     */
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

    /**
     * 规范化分区名称。
     *
     * @param partitionName 原始分区名称
     * @return 规范化后的分区标识
     */
    public String normalizePartition(String partitionName) {
        String name = partitionName == null || partitionName.isBlank()
                ? defaultPartition
                : partitionName.trim().toLowerCase();
        String normalized = PARTITION_ALIASES.getOrDefault(name, name);
        return PARTITION_TIDS.containsKey(normalized) ? normalized : "knowledge";
    }

    /**
     * 判断是否启用了 LLM。
     *
     * @return 是否已配置可用的 LLM Key
     */
    public boolean llmEnabled() {
        return llmApiKey != null && !llmApiKey.isBlank();
    }

    /**
     * 返回当前运行模式。
     *
     * @return `llm_agent` 或 `rules`
     */
    public String runtimeMode() {
        return llmEnabled() ? "llm_agent" : "rules";
    }

    /**
     * 根据分区名称获取对应的 tid。
     *
     * @param partitionName 分区名称
     * @return B 站分区 tid
     */
    public int partitionTid(String partitionName) {
        return PARTITION_TIDS.get(normalizePartition(partitionName));
    }
}
