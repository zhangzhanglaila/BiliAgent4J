package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseServiceTest {

    @Test
    void addDocumentAndRetrieveReturnsRelevantMatch() throws Exception {
        KnowledgeBaseService service = new KnowledgeBaseService(appProperties(), new ObjectMapper(), new LocalEmbeddingService());

        Map<String, Object> result = service.addDocument(new KnowledgeBaseService.KnowledgeDocument(
                "doc:ai-playbook",
                "AI 剪辑自动化实战手册，包含选题拆解、脚本生成和复盘流程。",
                Map.of("source", "test_case")
        ));

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("chunk_count")).isEqualTo(1);

        Map<String, Object> retrieved = service.retrieve("AI 剪辑 复盘", 3, null);
        assertThat(retrieved.get("query")).isEqualTo("AI 剪辑 复盘");

        List<?> matches = (List<?>) retrieved.get("matches");
        assertThat(matches).isNotEmpty();
        assertThat(((Map<?, ?>) matches.get(0)).get("id")).isEqualTo("doc:ai-playbook");
    }

    private AppProperties appProperties() {
        AppProperties properties = new AppProperties();
        properties.setVectorDbPath(testWorkDir("knowledge-base").resolve("vector_db").toString());
        return properties;
    }

    private Path testWorkDir(String name) {
        try {
            Path path = Path.of("target", "test-work", name + "-" + System.nanoTime()).toAbsolutePath();
            Files.createDirectories(path);
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test workspace", exception);
        }
    }
}
