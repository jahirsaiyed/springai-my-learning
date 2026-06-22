package com.example.agents.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for MCP resilience (retry + circuit breaker).
 * All values are bound from the {@code app.mcp.*} prefix in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.mcp")
public class McpResilienceProperties {

    /** Maximum number of retries after the initial attempt. Total attempts = 1 + retryMaxAttempts. */
    private int retryMaxAttempts = 2;

    /** Initial backoff duration in milliseconds; doubles on each subsequent retry. */
    private long retryInitialBackoffMs = 500;

    /**
     * Number of consecutive failed call sequences that trips the circuit breaker to OPEN.
     */
    private int circuitBreakerFailureThreshold = 3;

    /** Duration in milliseconds before the circuit breaker transitions from OPEN to HALF-OPEN. */
    private long circuitBreakerResetTimeoutMs = 30000;

    /**
     * Number of consecutive failures after which the orchestrator should consider escalating.
     * Tracked via {@link ResilientToolCallback#getConsecutiveFailures()}.
     */
    private int maxFailuresBeforeEscalation = 3;

    // --- Getters ---

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public long getRetryInitialBackoffMs() {
        return retryInitialBackoffMs;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public long getCircuitBreakerResetTimeoutMs() {
        return circuitBreakerResetTimeoutMs;
    }

    public int getMaxFailuresBeforeEscalation() {
        return maxFailuresBeforeEscalation;
    }

    // --- Setters (required for Spring Boot @ConfigurationProperties binding) ---

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public void setRetryInitialBackoffMs(long retryInitialBackoffMs) {
        this.retryInitialBackoffMs = retryInitialBackoffMs;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerResetTimeoutMs(long circuitBreakerResetTimeoutMs) {
        this.circuitBreakerResetTimeoutMs = circuitBreakerResetTimeoutMs;
    }

    public void setMaxFailuresBeforeEscalation(int maxFailuresBeforeEscalation) {
        this.maxFailuresBeforeEscalation = maxFailuresBeforeEscalation;
    }
}
