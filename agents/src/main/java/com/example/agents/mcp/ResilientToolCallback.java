package com.example.agents.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps a {@link ToolCallback} with retry (exponential backoff) and circuit breaker logic.
 *
 * <p>Retry behaviour: 1 initial attempt + up to {@code retryMaxAttempts} retries.
 * Backoff starts at {@code retryInitialBackoffMs} and doubles each attempt.
 *
 * <p>Circuit breaker: opens after {@code circuitBreakerFailureThreshold} consecutive
 * exhausted-retry sequences. While OPEN, calls return {@code MCP_UNAVAILABLE} immediately
 * without touching the delegate. Transitions to HALF-OPEN after
 * {@code circuitBreakerResetTimeoutMs}, allowing one probe call through.
 *
 * <p>Consecutive failure counter: incremented each time all retries are exhausted;
 * reset to 0 on any successful delegate call.
 */
public class ResilientToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ResilientToolCallback.class);

    private static final String UNAVAILABLE_MESSAGE =
            "MCP_UNAVAILABLE: The order system is temporarily unavailable. "
            + "Please try again in a few minutes.";

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private final ToolCallback delegate;
    private final McpResilienceProperties props;

    // Circuit breaker state
    private volatile CircuitState circuitState = CircuitState.CLOSED;
    private final AtomicInteger circuitFailureCount = new AtomicInteger(0);
    private final AtomicLong circuitOpenedAt = new AtomicLong(0);

    // Consecutive failed call-sequence counter (for escalation)
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public ResilientToolCallback(ToolCallback delegate, McpResilienceProperties props) {
        this.delegate = delegate;
        this.props = props;
    }

    // --- ToolCallback delegation ---

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * Executes the tool call with retry and circuit breaker protection.
     * Never throws; returns {@code MCP_UNAVAILABLE: ...} on exhausted retries or open circuit.
     */
    @Override
    public String call(String toolInput) {
        if (isCircuitOpen()) {
            log.warn("Circuit breaker OPEN for tool '{}' — returning unavailable",
                    delegate.getToolDefinition().name());
            return UNAVAILABLE_MESSAGE;
        }

        int totalAttempts = 1 + props.getRetryMaxAttempts();
        long backoffMs = props.getRetryInitialBackoffMs();
        Exception lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                String result = delegate.call(toolInput);
                onSuccess();
                return result;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Tool '{}' attempt {}/{} failed: {}",
                        delegate.getToolDefinition().name(), attempt, totalAttempts, ex.getMessage());

                if (attempt < totalAttempts) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                }
            }
        }

        // All attempts exhausted
        onCallSequenceFailed(lastException);
        return UNAVAILABLE_MESSAGE;
    }

    /** Resilience logic lives in {@link #call(String)}; ToolContext is ignored. */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return call(toolInput);
    }

    // --- Consecutive failure tracking (public API for orchestrator) ---

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void resetConsecutiveFailures() {
        consecutiveFailures.set(0);
    }

    // --- Internal helpers ---

    private boolean isCircuitOpen() {
        if (circuitState == CircuitState.CLOSED) {
            return false;
        }
        if (circuitState == CircuitState.OPEN) {
            long elapsed = System.currentTimeMillis() - circuitOpenedAt.get();
            if (elapsed >= props.getCircuitBreakerResetTimeoutMs()) {
                circuitState = CircuitState.HALF_OPEN;
                log.info("Circuit breaker transitioning to HALF-OPEN for tool '{}'",
                        delegate.getToolDefinition().name());
                return false; // allow the probe call
            }
            return true;
        }
        // HALF_OPEN: allow one probe call through
        return false;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        if (circuitState != CircuitState.CLOSED) {
            log.info("Circuit breaker CLOSED after successful call for tool '{}'",
                    delegate.getToolDefinition().name());
            circuitState = CircuitState.CLOSED;
            circuitFailureCount.set(0);
        }
    }

    private void onCallSequenceFailed(Exception lastException) {
        consecutiveFailures.incrementAndGet();
        int failures = circuitFailureCount.incrementAndGet();
        log.error("Tool '{}' exhausted all retries. Circuit failure count: {}/{}",
                delegate.getToolDefinition().name(), failures,
                props.getCircuitBreakerFailureThreshold(), lastException);

        if (failures >= props.getCircuitBreakerFailureThreshold()
                && circuitState != CircuitState.OPEN) {
            circuitState = CircuitState.OPEN;
            circuitOpenedAt.set(System.currentTimeMillis());
            log.error("Circuit breaker OPEN for tool '{}' after {} consecutive sequence failures",
                    delegate.getToolDefinition().name(), failures);
        }
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
