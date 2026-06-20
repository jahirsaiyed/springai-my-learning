package com.example.memory.common;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Splits text content into chunks suitable for embedding.
 * Uses token-based splitting for consistent chunk sizes.
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    private final TokenTextSplitter splitter;

    public TextChunker() {
        this.splitter = new TokenTextSplitter(
            DEFAULT_CHUNK_SIZE,
            DEFAULT_CHUNK_OVERLAP,
            5,
            10000,
            true
        );
    }

    public List<String> chunk(String content) {
        Document doc = new Document(content);
        List<Document> chunks = splitter.split(List.of(doc));
        return chunks.stream()
            .map(Document::getText)
            .toList();
    }

    public List<String> chunk(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        List<Document> chunks = splitter.split(List.of(doc));
        return chunks.stream()
            .map(Document::getText)
            .toList();
    }
}
