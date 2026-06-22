package com.example.memory.cache;

import com.example.memory.common.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L2 Cache: pgvector-based semantic similarity cache.
 * Finds cached responses for semantically similar queries using cosine similarity.
 *
 * Flow:
 * 1. Compute embedding for the query
 * 2. Search response_cache table for vectors with cosine similarity > threshold
 * 3. If found and not expired, return cached response
 * 4. If not found, caller proceeds to full LLM pipeline, then stores result here
 */
@Component
public class PgVectorL2Cache {

    private static final Logger log = LoggerFactory.getLogger(PgVectorL2Cache.class);
    private static final double SIMILARITY_THRESHOLD = 0.95;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final QueryClassifier queryClassifier;

    public PgVectorL2Cache(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService,
                           QueryClassifier queryClassifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.queryClassifier = queryClassifier;
    }

    private QueryType classifyQuery(String query) {
        return queryClassifier.classify(query);
    }

    /**
     * Search for a semantically similar cached response.
     */
    public Optional<CacheEntry> get(UUID tenantId, String query) {
        float[] embedding = embeddingService.embed(query);
        String vectorLiteral = toVectorLiteral(embedding);
        QueryType queryType = classifyQuery(query);

        try {
            List<CacheEntry> results = jdbcTemplate.query(
                """
                SELECT response, query_type,
                       EXTRACT(EPOCH FROM created_at) * 1000 AS cached_at_ms,
                       EXTRACT(EPOCH FROM expires_at) * 1000 AS expires_at_ms,
                       1 - (query_embedding <=> ?::vector) AS similarity
                FROM response_cache
                WHERE tenant_id = ?::uuid
                  AND query_type = ?
                  AND expires_at > NOW()
                  AND 1 - (query_embedding <=> ?::vector) > ?
                ORDER BY similarity DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new CacheEntry(
                    rs.getString("response"),
                    QueryType.valueOf(rs.getString("query_type")),
                    rs.getLong("cached_at_ms"),
                    rs.getLong("expires_at_ms")
                ),
                vectorLiteral,
                tenantId.toString(),
                queryType.name(),
                vectorLiteral,
                SIMILARITY_THRESHOLD
            );

            if (!results.isEmpty()) {
                log.debug("L2 cache hit for tenant {} (similarity > {})", tenantId, SIMILARITY_THRESHOLD);
                return Optional.of(results.getFirst());
            }
        } catch (Exception e) {
            log.debug("L2 cache lookup failed (table may not exist): {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Store a response in L2 semantic cache.
     */
    public void put(UUID tenantId, String query, String response, QueryType queryType) {
        float[] embedding = embeddingService.embed(query);
        String vectorLiteral = toVectorLiteral(embedding);
        String queryHash = RedisL1Cache.hashQuery(query);

        Instant expiresAt = Instant.now().plus(queryType.getTtl());

        try {
            jdbcTemplate.update(
                """
                INSERT INTO response_cache (id, tenant_id, query_embedding, query_hash, response, query_type, expires_at)
                VALUES (gen_random_uuid(), ?::uuid, ?::vector, ?, ?, ?, ?::timestamptz)
                """,
                tenantId.toString(),
                vectorLiteral,
                queryHash,
                response,
                queryType.name(),
                expiresAt.toString()
            );
            log.debug("L2 cache put for tenant {} type {}", tenantId, queryType);
        } catch (Exception e) {
            log.warn("Failed to store L2 cache entry: {}", e.getMessage());
        }
    }

    /**
     * Invalidate all cached entries for a tenant.
     */
    public void invalidateTenant(UUID tenantId) {
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM response_cache WHERE tenant_id = ?::uuid",
                tenantId.toString()
            );
            log.info("Invalidated {} L2 cache entries for tenant {}", deleted, tenantId);
        } catch (Exception e) {
            log.debug("L2 cache invalidation failed: {}", e.getMessage());
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
