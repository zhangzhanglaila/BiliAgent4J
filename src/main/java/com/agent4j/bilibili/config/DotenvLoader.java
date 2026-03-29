package com.agent4j.bilibili.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static void load() {
        for (Path path : candidatePaths()) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String line = rawLine == null ? "" : rawLine.trim();
                    if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int split = line.indexOf('=');
                    String key = line.substring(0, split).trim();
                    String value = line.substring(split + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!key.isBlank() && System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
                return;
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private static List<Path> candidatePaths() {
        return List.of(
                Path.of(".env"),
                Path.of("D:\\Agent4J\\.env")
        );
    }
}
