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

    public List<Double> embedQuery(String text) {
        return embed(text);
    }

    public List<List<Double>> embedDocuments(List<String> texts) {
        List<List<Double>> vectors = new ArrayList<>();
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

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

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build deterministic embedding digest", exception);
        }
    }
}
