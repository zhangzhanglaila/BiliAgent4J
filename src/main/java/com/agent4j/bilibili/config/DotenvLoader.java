package com.agent4j.bilibili.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DotenvLoader {

    /**
     * 工具类，禁止实例化。
     */
    private DotenvLoader() {
    }

    /**
     * 按候选路径加载 .env 配置。
     * 仅在系统属性和环境变量都未设置时写入对应键值。
     */
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

    /**
     * 返回按顺序尝试加载的 .env 路径。
     *
     * @return 可用的配置文件候选列表
     */
    private static List<Path> candidatePaths() {
        return List.of(
                Path.of(".env"),
                Path.of("D:\\Agent4J\\.env")
        );
    }
}
