package com.agent4j.bilibili.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LocalEmbeddingService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]{1,6}|[a-z0-9]{2,24}");
    private static final int DEFAULT_DIMENSION = 192;

    /**
     * 对查询文本生成嵌入向量。
     *
     * @param text 查询文本
     * @return 向量列表
     */
    public List<Double> embedQuery(String text) {
        return embed(text);
    }

    /**
     * 批量为文档生成嵌入向量。
     *
     * @param texts 文档列表
     * @return 向量列表
     */
    public List<List<Double>> embedDocuments(List<String> texts) {
        List<List<Double>> vectors = new ArrayList<>();
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    /**
     * 从文本中提取关键词分词。
     *
     * @param text 原始文本
     * @return 分词列表
     */
    public List<String> keywordTokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(String.valueOf(text == null ? "" : text).toLowerCase());
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * @param left 左侧向量
     * @param right 右侧向量
     * @return 余弦相似度
     */
    public double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int size = Math.min(left.size(), right.size());
        double sum = 0.0;
        for (int index = 0; index < size; index++) {
            sum += left.get(index) * right.get(index);
        }
        return sum;
    }

    /**
     * 计算查询与文本之间的词汇重叠得分。
     *
     * @param query 查询文本
     * @param text 目标文本
     * @return 重叠得分，0 到 1 之间
     */
    public double lexicalOverlapScore(String query, String text) {
        List<String> queryTokens = keywordTokens(query);
        List<String> textTokens = keywordTokens(text);
        if (queryTokens.isEmpty() || textTokens.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String token : queryTokens) {
            if (textTokens.contains(token)) {
                overlap++;
            }
        }
        return overlap / (double) Math.max(queryTokens.size(), 1);
    }

    /**
     * 获取嵌入向量的维度。
     *
     * @return 向量维度
     */
    public int dimension() {
        return DEFAULT_DIMENSION;
    }

    /**
     * 生成文本的确定性嵌入向量。
     * 使用分词 SHA256 哈希分布填充向量，再进行 L2 归一化。
     *
     * @param text 输入文本
     * @return 归一化后的向量
     */
    private List<Double> embed(String text) {
        double[] vector = new double[DEFAULT_DIMENSION];
        for (String token : keywordTokens(text)) {
            byte[] digest = sha256(token);
            int bound = Math.min(digest.length, DEFAULT_DIMENSION / 8);
            for (int index = 0; index < bound; index++) {
                int unsigned = digest[index] & 0xff;
                int slot = (unsigned + index * 17) % DEFAULT_DIMENSION;
                vector[slot] += ((unsigned % 13) + 1) / 13.0;
            }
        }
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm <= 0.0) {
            norm = 1.0;
        }
        List<Double> normalized = new ArrayList<>(DEFAULT_DIMENSION);
        for (double value : vector) {
            normalized.add(value / norm);
        }
        return normalized;
    }

    /**
     * 计算字符串的 SHA256 摘要。
     *
     * @param token 输入字符串
     * @return SHA256 字节数组
     */
    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build deterministic embedding digest", exception);
        }
    }
}
