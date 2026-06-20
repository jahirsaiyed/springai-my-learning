package com.example.memory.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles parsing different document formats (PDF, HTML, Markdown, etc.)
 * into plain text using Apache Tika via Spring AI's TikaDocumentReader.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    public String extractText(byte[] content, String fileName) {
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        try {
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<org.springframework.ai.document.Document> docs = reader.read();
            StringBuilder sb = new StringBuilder();
            for (var doc : docs) {
                sb.append(doc.getText()).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Tika parsing failed for {}, falling back to raw text", fileName, e);
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    public String extractText(String rawContent) {
        return rawContent;
    }
}
