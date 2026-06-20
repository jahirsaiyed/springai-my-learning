package com.example.memory.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Elasticsearch indexes for tenant knowledge base documents.
 * Each tenant gets a separate index: knowledge_{tenantId}
 */
@Service
public class ElasticsearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexService.class);

    private final ElasticsearchClient client;

    public ElasticsearchIndexService(ElasticsearchClient client) {
        this.client = client;
    }

    public String indexName(UUID tenantId) {
        return "knowledge_" + tenantId.toString().replace("-", "");
    }

    public void ensureIndex(UUID tenantId) {
        String index = indexName(tenantId);
        try {
            boolean exists = client.indices().exists(e -> e.index(index)).value();
            if (!exists) {
                client.indices().create(c -> c
                    .index(index)
                    .mappings(m -> m
                        .properties("title", p -> p.text(t -> t.analyzer("standard")))
                        .properties("content", p -> p.text(t -> t.analyzer("standard")))
                        .properties("document_id", p -> p.keyword(k -> k))
                        .properties("tenant_id", p -> p.keyword(k -> k))
                        .properties("chunk_index", p -> p.integer(i -> i))
                    )
                    .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                    )
                );
                log.info("Created Elasticsearch index: {}", index);
            }
        } catch (IOException e) {
            log.warn("Failed to create Elasticsearch index {}: {}", index, e.getMessage());
        }
    }

    public void indexChunk(UUID tenantId, String chunkId, String documentId,
                            String title, String content, int chunkIndex) {
        String index = indexName(tenantId);
        try {
            ensureIndex(tenantId);
            client.index(i -> i
                .index(index)
                .id(chunkId)
                .document(Map.of(
                    "title", title,
                    "content", content,
                    "document_id", documentId,
                    "tenant_id", tenantId.toString(),
                    "chunk_index", chunkIndex
                ))
            );
        } catch (IOException e) {
            log.warn("Failed to index chunk {} in ES: {}", chunkId, e.getMessage());
        }
    }

    public void indexChunksBulk(UUID tenantId, List<ChunkDocument> chunks) {
        String index = indexName(tenantId);
        try {
            ensureIndex(tenantId);
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (ChunkDocument chunk : chunks) {
                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .index(index)
                        .id(chunk.chunkId())
                        .document(Map.of(
                            "title", chunk.title(),
                            "content", chunk.content(),
                            "document_id", chunk.documentId(),
                            "tenant_id", tenantId.toString(),
                            "chunk_index", chunk.chunkIndex()
                        ))
                    )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.warn("ES bulk index error: {}", item.error().reason());
                    }
                }
            } else {
                log.debug("Bulk indexed {} chunks to {}", chunks.size(), index);
            }
        } catch (IOException e) {
            log.warn("Failed to bulk index chunks in ES: {}", e.getMessage());
        }
    }

    public void deleteByDocumentId(UUID tenantId, String documentId) {
        String index = indexName(tenantId);
        try {
            client.deleteByQuery(d -> d
                .index(index)
                .query(q -> q.term(t -> t.field("document_id").value(documentId)))
            );
            log.debug("Deleted ES documents for document_id={}", documentId);
        } catch (IOException e) {
            log.warn("Failed to delete ES documents for {}: {}", documentId, e.getMessage());
        }
    }

    public record ChunkDocument(
        String chunkId, String documentId, String title,
        String content, int chunkIndex
    ) {}
}
