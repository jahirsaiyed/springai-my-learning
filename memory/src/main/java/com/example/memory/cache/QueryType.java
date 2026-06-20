package com.example.memory.cache;

import java.time.Duration;

/**
 * Classifies queries for TTL determination.
 * Different query types have different cache durations based on data volatility.
 */
public enum QueryType {

    POLICY_FAQ(Duration.ofHours(24)),
    ORDER_STATUS(Duration.ofMinutes(5)),
    REFUND_STATUS(Duration.ofMinutes(10)),
    PROCEDURAL(Duration.ofHours(1)),
    GENERAL(Duration.ofMinutes(30));

    private final Duration ttl;

    QueryType(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getTtl() {
        return ttl;
    }

    public long getTtlSeconds() {
        return ttl.toSeconds();
    }
}
