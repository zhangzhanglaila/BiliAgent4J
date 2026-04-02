package com.agent4j.bilibili.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeFileTextExtractor {

    public String extractText(String filename, byte[] rawBytes) {
        String suffix = suffix(filename);
        byte[] safeBytes = rawBytes == null ? new byte[0] : rawBytes;
        return switch (suffix) {
            case ".txt", ".md" -> new String(safeBytes, StandardCharsets.UTF_8);
            case ".docx" -> extractDocxText(safeBytes);
            case ".pdf" -> extractPdfText(safeBytes);
            default -> throw new IllegalArgumentException("Unsupported knowledge file type: " + suffix);
        };
    }

    private String extractDocxText(byte[] rawBytes) {
        try (
                ByteArrayInputStream input = new ByteArrayInputStream(rawBytes);
                XWPFDocument document = new XWPFDocument(input);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)
        ) {
            return extractor.getText();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to read DOCX knowledge file.", exception);
        }
    }

    private String extractPdfText(byte[] rawBytes) {
        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to read PDF knowledge file.", exception);
        }
    }

    private String suffix(String filename) {
        String cleanFilename = filename == null ? "" : filename.trim().toLowerCase();
        int index = cleanFilename.lastIndexOf('.');
        return index < 0 ? "" : cleanFilename.substring(index);
    }
}
