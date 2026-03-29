package com.agent4j.bilibili.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtils {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fffA-Za-z0-9]{2,}");
    private static final Pattern BVID_PATTERN = Pattern.compile("(BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE);

    private TextUtils() {
    }

    public static String cleanSpaces(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    public static String cleanCopyText(String value) {
        return cleanSpaces(value).replaceAll("^[ ：:，,。;；_\\-]+|[ ：:，,。;；_\\-]+$", "");
    }

    public static List<String> extractKeywords(String value, int limit) {
        List<String> result = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(cleanCopyText(value));
        while (matcher.find() && result.size() < limit) {
            String keyword = matcher.group();
            if (!result.contains(keyword)) {
                result.add(keyword);
            }
        }
        return result;
    }

    public static String findBvid(String value) {
        Matcher matcher = BVID_PATTERN.matcher(value == null ? "" : value);
        if (matcher.find()) {
            String matched = matcher.group(1);
            return "BV" + matched.substring(2);
        }
        return "";
    }

    public static int safeInt(Object value) {
        try {
            if (value == null) {
                return 0;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return 0;
            }
            return (int) Math.round(Double.parseDouble(text));
        } catch (Exception exception) {
            return 0;
        }
    }

    public static long safeLong(Object value) {
        try {
            if (value == null) {
                return 0L;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return 0L;
            }
            return Math.round(Double.parseDouble(text));
        } catch (Exception exception) {
            return 0L;
        }
    }

    public static double safeDouble(Object value) {
        try {
            if (value == null) {
                return 0.0;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(text);
        } catch (Exception exception) {
            return 0.0;
        }
    }

    public static int safeMetricInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return 0;
        }
        double multiplier = 1.0;
        if (text.endsWith("万")) {
            multiplier = 10_000;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("亿")) {
            multiplier = 100_000_000;
            text = text.substring(0, text.length() - 1);
        }
        try {
            return (int) Math.round(Double.parseDouble(text.replace(",", "")) * multiplier);
        } catch (Exception exception) {
            return 0;
        }
    }
}
