package com.example.memory.cache;

/**
 * Represents a cached response entry with metadata.
 */
public record CacheEntry(
    String response,
    QueryType queryType,
    long cachedAtEpochMs,
    long expiresAtEpochMs
) {

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtEpochMs;
    }

    public static CacheEntry of(String response, QueryType queryType) {
        long now = System.currentTimeMillis();
        return new CacheEntry(
            response,
            queryType,
            now,
            now + queryType.getTtl().toMillis()
        );
    }
}
