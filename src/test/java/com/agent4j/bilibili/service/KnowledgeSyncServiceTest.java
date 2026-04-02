package com.agent4j.bilibili.service;

import com.agent4j.bilibili.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeSyncServiceTest {

    @Test
    void ingestUploadedDocxFileParsesReadableText() throws Exception {
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(appProperties(), new ObjectMapper(), new LocalEmbeddingService());
        KnowledgeSyncService service = new KnowledgeSyncService(
                mock(TopicDataService.class),
                knowledgeBaseService,
                new KnowledgeFileTextExtractor(),
                new ObjectMapper()
        );

        Map<String, Object> result = service.ingestUploadedFile("playbook.docx", buildDocx("AI 选题知识库"), Map.of("source_channel", "test"));

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("file_type")).isEqualTo(".docx");
        assertThat(knowledgeBaseService.count()).isGreaterThan(0);

        Map<String, Object> sample = knowledgeBaseService.sample(1, 0, Map.of("source", "uploaded_file"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) sample.get("items")).get(0);
        assertThat(String.valueOf(item.get("text"))).contains("AI 选题知识库");
    }

    @Test
    void ingestUploadedPdfFileParsesReadableText() throws Exception {
        System.setProperty("pdfbox.fontcache", testWorkDir("pdfbox-cache").toString());
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(appProperties(), new ObjectMapper(), new LocalEmbeddingService());
        KnowledgeSyncService service = new KnowledgeSyncService(
                mock(TopicDataService.class),
                knowledgeBaseService,
                new KnowledgeFileTextExtractor(),
                new ObjectMapper()
        );

        Map<String, Object> result = service.ingestUploadedFile("playbook.pdf", buildPdf("AI knowledge workflow"), Map.of());

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("file_type")).isEqualTo(".pdf");
        assertThat(knowledgeBaseService.count()).isGreaterThan(0);

        Map<String, Object> sample = knowledgeBaseService.sample(1, 0, Map.of("source", "uploaded_file"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) sample.get("items")).get(0);
        assertThat(String.valueOf(item.get("text"))).contains("AI knowledge workflow");
    }

    private AppProperties appProperties() {
        AppProperties properties = new AppProperties();
        properties.setVectorDbPath(testWorkDir("knowledge-sync").resolve("vector_db").toString());
        return properties;
    }

    private byte[] buildDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] buildPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
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
