package com.example.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Unified semantic cache service that orchestrates the full cache pipeline:
 *
 *   Query -> L1 Redis (exact hash) -> L2 pgvector (semantic similarity) -> Cache miss
 *
 * On cache miss, the caller (agent) executes the full LLM pipeline, then calls
 * storeResponse() to populate both cache layers.
 *
 * Cache invalidation is triggered when the knowledge base is updated.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final RedisL1Cache l1Cache;
    private final PgVectorL2Cache l2Cache;
    private final RagCache ragCache;
    private final QueryClassifier queryClassifier;
    private final CacheMetrics cacheMetrics;

    public SemanticCacheService(RedisL1Cache l1Cache,
                                 PgVectorL2Cache l2Cache,
                                 RagCache ragCache,
                                 QueryClassifier queryClassifier,
                                 CacheMetrics cacheMetrics) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
        this.ragCache = ragCache;
        this.queryClassifier = queryClassifier;
        this.cacheMetrics = cacheMetrics;
    }

    /**
     * Attempt to retrieve a cached response for the given query.
     * Checks L1 (Redis exact match) first, then L2 (pgvector semantic similarity).
     */
    public Optional<CachedResponse> lookup(UUID tenantId, String query) {
        QueryType queryType = queryClassifier.classify(query);

        // Skip cache for transactional queries (order/refund status) —
        // responses are user-specific and must not be served from cache
        if (!queryType.isCacheable()) {
            log.debug("Cache SKIP for tenant {} type {} (not cacheable)", tenantId, queryType);
            cacheMetrics.recordMiss(tenantId);
            return Optional.empty();
        }

        // L1: Redis exact-match (fastest)
        Optional<CacheEntry> l1Result = l1Cache.get(tenantId, query);
        if (l1Result.isPresent()) {
            cacheMetrics.recordL1Hit(tenantId);
            log.debug("Cache L1 HIT for tenant {} type {}", tenantId, queryType);
            return Optional.of(new CachedResponse(
                l1Result.get().response(), CacheLayer.L1_REDIS, queryType));
        }

        // L2: pgvector semantic similarity
        Optional<CacheEntry> l2Result = l2Cache.get(tenantId, query);
        if (l2Result.isPresent()) {
            cacheMetrics.recordL2Hit(tenantId);
            // Promote to L1 for faster subsequent lookups
            l1Cache.put(tenantId, query, l2Result.get());
            log.debug("Cache L2 HIT for tenant {} type {} (promoted to L1)", tenantId, queryType);
            return Optional.of(new CachedResponse(
                l2Result.get().response(), CacheLayer.L2_PGVECTOR, queryType));
        }

        cacheMetrics.recordMiss(tenantId);
        log.debug("Cache MISS for tenant {} type {}", tenantId, queryType);
        return Optional.empty();
    }

    /**
     * Store a response in both cache layers after a cache miss.
     */
    public void storeResponse(UUID tenantId, String query, String response) {
        QueryType queryType = queryClassifier.classify(query);

        // Don't cache transactional queries — responses are user-specific
        if (!queryType.isCacheable()) {
            log.debug("Cache SKIP store for tenant {} type {} (not cacheable)", tenantId, queryType);
            return;
        }

        CacheEntry entry = CacheEntry.of(response, queryType);

        l1Cache.put(tenantId, query, entry);
        l2Cache.put(tenantId, query, response, queryType);

        log.debug("Cached response for tenant {} type {} in L1+L2", tenantId, queryType);
    }

    public RagCache ragCache() {
        return ragCache;
    }

    public QueryType classifyQuery(String query) {
        return queryClassifier.classify(query);
    }

    /**
     * Invalidate all caches for a tenant.
     * Called when knowledge base is updated.
     */
    public void invalidateTenant(UUID tenantId) {
        l1Cache.invalidateTenant(tenantId);
        l2Cache.invalidateTenant(tenantId);
        ragCache.invalidateTenant(tenantId);
        log.info("Invalidated all cache layers for tenant {}", tenantId);
    }

    /**
     * Get cache statistics for a tenant.
     */
    public CacheMetrics.CacheStats getStats(UUID tenantId) {
        return cacheMetrics.getStats(tenantId);
    }

    /**
     * Get global cache statistics.
     */
    public CacheMetrics.CacheStats getGlobalStats() {
        return cacheMetrics.getGlobalStats();
    }

    public record CachedResponse(String response, CacheLayer layer, QueryType queryType) {}

    public enum CacheLayer {
        L1_REDIS,
        L2_PGVECTOR
    }
}
