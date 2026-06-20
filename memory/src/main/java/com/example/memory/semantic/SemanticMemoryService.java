package com.example.memory.semantic;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for semantic memory operations.
 * Manages knowledge base with vector embeddings for RAG.
 */
public interface SemanticMemoryService {

    KnowledgeDocument ingestDocument(UUID tenantId, String title, SourceType sourceType, String content);

    List<String> search(UUID tenantId, String query, int topK);

    /**
     * Hybrid search combining vector similarity + BM25 full-text search
     * with reciprocal rank fusion for improved retrieval quality.
     */
    List<String> hybridSearch(UUID tenantId, String query, int topK);

    void deleteDocument(UUID documentId);

    void supersede(UUID documentId, String newContent);
}
