package com.example.memory.cache;

import com.example.memory.common.EmbeddingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches RAG retrieval results (which knowledge chunks were retrieved for a query).
 * Higher similarity threshold (0.98) than response cache since we want near-exact
 * semantic matches to avoid serving stale retrieval results.
 *
 * This avoids the expensive vector search when the same (or nearly identical)
 * question is asked repeatedly. The LLM is still called, but with cached chunks.
 */
@Component
public class RagCache {

    private static final Logger log = LoggerFactory.getLogger(RagCache.class);
    private static final double SIMILARITY_THRESHOLD = 0.98;
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public RagCache(JdbcTemplate jdbcTemplate,
                    EmbeddingService embeddingService,
                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Look up cached chunk IDs for a semantically similar query.
     */
    public Optional<List<String>> get(UUID tenantId, String query) {
        float[] embedding = embeddingService.embed(query);
        String vectorLiteral = toVectorLiteral(embedding);

        try {
            List<String> results = jdbcTemplate.query(
                """
                SELECT chunk_ids_json,
                       1 - (query_embedding <=> ?::vector) AS similarity
                FROM rag_cache
                WHERE tenant_id = ?::uuid
                  AND expires_at > NOW()
                  AND 1 - (query_embedding <=> ?::vector) > ?
                ORDER BY similarity DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getString("chunk_ids_json"),
                vectorLiteral,
                tenantId.toString(),
                vectorLiteral,
                SIMILARITY_THRESHOLD
            );

            if (!results.isEmpty()) {
                List<String> chunkIds = objectMapper.readValue(
                    results.getFirst(), new TypeReference<List<String>>() {});
                log.debug("RAG cache hit for tenant {} ({} chunks)", tenantId, chunkIds.size());
                return Optional.of(chunkIds);
            }
        } catch (Exception e) {
            log.debug("RAG cache lookup failed: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Store RAG retrieval results.
     */
    public void put(UUID tenantId, String query, List<String> chunkIds) {
        float[] embedding = embeddingService.embed(query);
        String vectorLiteral = toVectorLiteral(embedding);
        Instant expiresAt = Instant.now().plus(DEFAULT_TTL);

        try {
            String chunkIdsJson = objectMapper.writeValueAsString(chunkIds);
            jdbcTemplate.update(
                """
                INSERT INTO rag_cache (id, tenant_id, query_embedding, chunk_ids_json, expires_at)
                VALUES (gen_random_uuid(), ?::uuid, ?::vector, ?::jsonb, ?::timestamptz)
                """,
                tenantId.toString(),
                vectorLiteral,
                chunkIdsJson,
                expiresAt.toString()
            );
            log.debug("RAG cache put for tenant {} ({} chunks)", tenantId, chunkIds.size());
        } catch (Exception e) {
            log.warn("Failed to store RAG cache entry: {}", e.getMessage());
        }
    }

    /**
     * Invalidate all RAG cache entries for a tenant.
     * Called when knowledge base is updated.
     */
    public void invalidateTenant(UUID tenantId) {
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM rag_cache WHERE tenant_id = ?::uuid",
                tenantId.toString()
            );
            if (deleted > 0) {
                log.info("Invalidated {} RAG cache entries for tenant {}", deleted, tenantId);
            }
        } catch (Exception e) {
            log.debug("RAG cache invalidation failed: {}", e.getMessage());
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
