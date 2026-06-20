package com.example.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks cache hit/miss metrics per tenant for observability.
 * Stores counters in Redis for persistence across restarts.
 */
@Component
public class CacheMetrics {

    private static final Logger log = LoggerFactory.getLogger(CacheMetrics.class);
    private static final String METRICS_PREFIX = "cache:metrics:";

    private final StringRedisTemplate redisTemplate;

    // In-memory counters for fast access (flushed to Redis periodically)
    private final AtomicLong l1Hits = new AtomicLong();
    private final AtomicLong l2Hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public CacheMetrics(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordL1Hit(UUID tenantId) {
        l1Hits.incrementAndGet();
        increment(tenantId, "l1_hits");
    }

    public void recordL2Hit(UUID tenantId) {
        l2Hits.incrementAndGet();
        increment(tenantId, "l2_hits");
    }

    public void recordMiss(UUID tenantId) {
        misses.incrementAndGet();
        increment(tenantId, "misses");
    }

    public CacheStats getStats(UUID tenantId) {
        long h1 = getLong(tenantId, "l1_hits");
        long h2 = getLong(tenantId, "l2_hits");
        long m = getLong(tenantId, "misses");
        long total = h1 + h2 + m;
        double hitRate = total > 0 ? (double) (h1 + h2) / total * 100 : 0;

        return new CacheStats(h1, h2, m, total, hitRate);
    }

    public CacheStats getGlobalStats() {
        long total = l1Hits.get() + l2Hits.get() + misses.get();
        double hitRate = total > 0 ? (double) (l1Hits.get() + l2Hits.get()) / total * 100 : 0;
        return new CacheStats(l1Hits.get(), l2Hits.get(), misses.get(), total, hitRate);
    }

    private void increment(UUID tenantId, String metric) {
        try {
            redisTemplate.opsForHash().increment(
                METRICS_PREFIX + tenantId, metric, 1);
        } catch (Exception e) {
            log.debug("Failed to record cache metric: {}", e.getMessage());
        }
    }

    private long getLong(UUID tenantId, String metric) {
        try {
            Object value = redisTemplate.opsForHash().get(METRICS_PREFIX + tenantId, metric);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public record CacheStats(
        long l1Hits,
        long l2Hits,
        long misses,
        long totalQueries,
        double hitRatePercent
    ) {}
}
