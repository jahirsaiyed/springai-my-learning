package com.example.agents.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<CircuitState> circuitState =
            new AtomicReference<>(CircuitState.CLOSED);
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
        return callWithResilience(toolInput, null);
    }

    /**
     * Executes the tool call with retry and circuit breaker protection, preserving ToolContext.
     * Never throws; returns {@code MCP_UNAVAILABLE: ...} on exhausted retries or open circuit.
     */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return callWithResilience(toolInput, toolContext);
    }

    /**
     * Internal resilience logic: retry with exponential backoff and circuit breaker.
     * Calls delegate with ToolContext if available, otherwise falls back to String-only call.
     */
    private String callWithResilience(String toolInput, ToolContext toolContext) {
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
                String result = (toolContext != null)
                    ? delegate.call(toolInput, toolContext)
                    : delegate.call(toolInput);
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

    // --- Consecutive failure tracking (public API for orchestrator) ---

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void resetConsecutiveFailures() {
        consecutiveFailures.set(0);
    }

    // --- Internal helpers ---

    private boolean isCircuitOpen() {
        CircuitState state = circuitState.get();
        if (state == CircuitState.CLOSED) {
            return false;
        }
        if (state == CircuitState.OPEN) {
            long elapsed = System.currentTimeMillis() - circuitOpenedAt.get();
            if (elapsed >= props.getCircuitBreakerResetTimeoutMs()) {
                // Only one thread wins the OPEN → HALF_OPEN transition
                if (circuitState.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    log.info("Circuit breaker transitioning to HALF-OPEN for tool '{}'",
                            delegate.getToolDefinition().name());
                }
                return false; // allow the probe call (winner and losers both proceed)
            }
            return true;
        }
        // HALF_OPEN: allow one probe call through
        return false;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        CircuitState prev = circuitState.getAndSet(CircuitState.CLOSED);
        if (prev != CircuitState.CLOSED) {
            log.info("Circuit breaker CLOSED after successful call for tool '{}'",
                    delegate.getToolDefinition().name());
            circuitFailureCount.set(0);
        }
    }

    private void onCallSequenceFailed(Exception lastException) {
        consecutiveFailures.incrementAndGet();
        int failures = circuitFailureCount.incrementAndGet();
        log.error("Tool '{}' exhausted all retries. Circuit failure count: {}/{}",
                delegate.getToolDefinition().name(), failures,
                props.getCircuitBreakerFailureThreshold(), lastException);

        if (failures >= props.getCircuitBreakerFailureThreshold()) {
            // Only one thread transitions to OPEN; update timestamp before CAS so it is
            // visible immediately once the state flip is observed by other threads.
            if (!circuitState.get().equals(CircuitState.OPEN)) {
                circuitOpenedAt.set(System.currentTimeMillis());
                if (circuitState.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)
                        || circuitState.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                    log.error("Circuit breaker OPEN for tool '{}' after {} consecutive sequence failures",
                            delegate.getToolDefinition().name(), failures);
                }
            }
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
