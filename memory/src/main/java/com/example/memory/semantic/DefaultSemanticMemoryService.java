package com.example.memory.semantic;

import com.example.memory.cache.SemanticCacheService;
import com.example.memory.common.EmbeddingService;
import com.example.memory.common.TextChunker;
import com.example.memory.search.ElasticsearchIndexService;
import com.example.memory.search.ElasticsearchIndexService.ChunkDocument;
import com.example.memory.search.HybridSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultSemanticMemoryService implements SemanticMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSemanticMemoryService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStore vectorStore;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final DocumentIngestionService ingestionService;
    private final SemanticCacheService cacheService;
    private final ElasticsearchIndexService esIndexService;
    private final HybridSearchService hybridSearchService;

    public DefaultSemanticMemoryService(KnowledgeDocumentRepository documentRepository,
                                         KnowledgeChunkRepository chunkRepository,
                                         VectorStore vectorStore,
                                         TextChunker textChunker,
                                         EmbeddingService embeddingService,
                                         DocumentIngestionService ingestionService,
                                         SemanticCacheService cacheService,
                                         ElasticsearchIndexService esIndexService,
                                         HybridSearchService hybridSearchService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.ingestionService = ingestionService;
        this.cacheService = cacheService;
        this.esIndexService = esIndexService;
        this.hybridSearchService = hybridSearchService;
    }

    @Override
    @Transactional
    public KnowledgeDocument ingestDocument(UUID tenantId, String title,
                                             SourceType sourceType, String content) {
        var doc = new KnowledgeDocument(tenantId, title, sourceType);
        doc = documentRepository.save(doc);

        String extractedText = ingestionService.extractText(content);
        List<String> chunks = textChunker.chunk(extractedText);

        List<ChunkDocument> esChunks = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            var chunk = new KnowledgeChunk(doc, chunkText, i);
            chunk.setMetadataJson("{\"document_id\":\"" + doc.getId()
                + "\",\"tenant_id\":\"" + tenantId
                + "\",\"chunk_index\":" + i + "}");
            chunkRepository.save(chunk);

            // Index in pgvector
            var vectorDoc = new Document(
                chunk.getId().toString(),
                chunkText,
                Map.of(
                    "document_id", doc.getId().toString(),
                    "tenant_id", tenantId.toString(),
                    "title", title,
                    "chunk_index", String.valueOf(i)
                )
            );
            vectorStore.add(List.of(vectorDoc));

            // Collect for ES bulk indexing
            esChunks.add(new ChunkDocument(
                chunk.getId().toString(), doc.getId().toString(),
                title, chunkText, i
            ));
        }

        // Bulk index in Elasticsearch
        if (!esChunks.isEmpty()) {
            esIndexService.indexChunksBulk(tenantId, esChunks);
        }

        // Invalidate caches since knowledge base changed
        cacheService.invalidateTenant(tenantId);

        log.info("Ingested document '{}' with {} chunks for tenant {} (vector + ES)",
            title, chunks.size(), tenantId);
        return doc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> search(UUID tenantId, String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .filterExpression("tenant_id == '" + tenantId + "'")
            .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream()
            .map(Document::getText)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> hybridSearch(UUID tenantId, String query, int topK) {
        return hybridSearchService.searchText(tenantId, query, topK);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
            .orElse(null);

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<String> chunkIds = chunks.stream()
            .map(c -> c.getId().toString())
            .toList();
        if (!chunkIds.isEmpty()) {
            vectorStore.delete(chunkIds);
        }

        // Delete from Elasticsearch
        if (doc != null) {
            esIndexService.deleteByDocumentId(doc.getTenantId(), documentId.toString());
        }

        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);

        // Invalidate caches
        if (doc != null) {
            cacheService.invalidateTenant(doc.getTenantId());
        }

        log.info("Deleted document {} with {} chunks (vector + ES)", documentId, chunks.size());
    }

    @Override
    @Transactional
    public void supersede(UUID documentId, String newContent) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        doc.setStatus(DocumentStatus.SUPERSEDED);
        doc.setEffectiveUntil(Instant.now());
        documentRepository.save(doc);

        // Delete old ES entries
        esIndexService.deleteByDocumentId(doc.getTenantId(), documentId.toString());

        // ingestDocument handles vector + ES indexing and cache invalidation
        ingestDocument(doc.getTenantId(), doc.getTitle(), doc.getSourceType(), newContent);
        log.info("Superseded document {} with new version", documentId);
    }
}
