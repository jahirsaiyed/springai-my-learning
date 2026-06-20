package com.example.memory.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * L1 Cache: Redis-based exact-match cache using SHA-256 hash of the query.
 * This is the fastest cache layer — O(1) lookup by hash key.
 */
@Component
public class RedisL1Cache {

    private static final Logger log = LoggerFactory.getLogger(RedisL1Cache.class);
    private static final String KEY_PREFIX = "cache:l1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisL1Cache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Look up an exact-match cached response.
     */
    public Optional<CacheEntry> get(UUID tenantId, String query) {
        String key = buildKey(tenantId, query);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }

        try {
            CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
            if (entry.isExpired()) {
                redisTemplate.delete(key);
                return Optional.empty();
            }
            log.debug("L1 cache hit for tenant {} query hash {}", tenantId, hashQuery(query));
            return Optional.of(entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize L1 cache entry", e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    /**
     * Store a response in L1 cache.
     */
    public void put(UUID tenantId, String query, CacheEntry entry) {
        String key = buildKey(tenantId, query);
        try {
            String json = objectMapper.writeValueAsString(entry);
            Duration ttl = entry.queryType().getTtl();
            redisTemplate.opsForValue().set(key, json, ttl);
            log.debug("L1 cache put for tenant {} query type {}", tenantId, entry.queryType());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize L1 cache entry", e);
        }
    }

    /**
     * Invalidate all cached entries for a tenant.
     * Used when knowledge base is updated.
     */
    public void invalidateTenant(UUID tenantId) {
        String pattern = KEY_PREFIX + tenantId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Invalidated {} L1 cache entries for tenant {}", keys.size(), tenantId);
        }
    }

    /**
     * Invalidate entries matching a specific query type for a tenant.
     */
    public void invalidateByType(UUID tenantId, QueryType queryType) {
        // Since we hash queries, we can't filter by type in L1.
        // For type-specific invalidation, we rely on TTL expiry.
        // Full tenant invalidation is the nuclear option.
        log.debug("Type-specific L1 invalidation not supported, relying on TTL for {} {}",
            tenantId, queryType);
    }

    private String buildKey(UUID tenantId, String query) {
        return KEY_PREFIX + tenantId + ":" + hashQuery(query);
    }

    static String hashQuery(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
