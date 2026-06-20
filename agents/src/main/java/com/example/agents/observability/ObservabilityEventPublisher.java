package com.example.agents.observability;

import com.example.agents.AgentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes structured observability events for agent interactions.
 * Uses structured logging with MDC-compatible key-value pairs
 * for easy integration with log aggregation tools (ELK, Loki, etc).
 */
@Component
public class ObservabilityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger("agent.observability");

    private final AgentDecisionTracker decisionTracker;
    private final TokenUsageEstimator tokenEstimator;

    public ObservabilityEventPublisher(AgentDecisionTracker decisionTracker,
                                        TokenUsageEstimator tokenEstimator) {
        this.decisionTracker = decisionTracker;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * Record a complete agent interaction with timing and token estimates.
     */
    public void recordInteraction(InteractionEvent event) {
        // Structured log
        log.info("agent_interaction tenant={} conversation={} agent={} confidence={} " +
                 "cached={} response_time_ms={} input_tokens={} output_tokens={}",
            event.tenantId(), event.conversationId(), event.agentType(),
            event.confidence(), event.cached(), event.responseTimeMs(),
            event.inputTokens(), event.outputTokens());

        // Persist decision
        decisionTracker.trackDecision(
            event.conversationId(),
            event.agentType().name(),
            event.toolUsed(),
            event.reasoning(),
            event.confidence(),
            event.responseTimeMs()
        );

        // Persist token usage (skip for cached responses)
        if (!event.cached() && event.inputTokens() > 0) {
            decisionTracker.trackTokenUsage(
                event.tenantId(),
                event.conversationId(),
                event.model(),
                event.inputTokens(),
                event.outputTokens()
            );
        }
    }

    /**
     * Create a timing context for measuring interaction duration.
     */
    public InteractionTimer startTimer(UUID tenantId, UUID conversationId) {
        return new InteractionTimer(tenantId, conversationId, this);
    }

    public record InteractionEvent(
        UUID tenantId,
        UUID conversationId,
        AgentType agentType,
        String toolUsed,
        String reasoning,
        double confidence,
        boolean cached,
        long responseTimeMs,
        int inputTokens,
        int outputTokens,
        String model
    ) {}

    /**
     * Timer that measures interaction duration and builds the event.
     */
    public static class InteractionTimer {
        private final UUID tenantId;
        private final UUID conversationId;
        private final ObservabilityEventPublisher publisher;
        private final long startTimeMs;

        InteractionTimer(UUID tenantId, UUID conversationId,
                         ObservabilityEventPublisher publisher) {
            this.tenantId = tenantId;
            this.conversationId = conversationId;
            this.publisher = publisher;
            this.startTimeMs = System.currentTimeMillis();
        }

        public void record(AgentType agentType, String toolUsed, String reasoning,
                           double confidence, boolean cached,
                           String inputText, String outputText, String model) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            var estimate = publisher.tokenEstimator.estimate(inputText, outputText);

            publisher.recordInteraction(new InteractionEvent(
                tenantId, conversationId, agentType,
                toolUsed, reasoning, confidence, cached, elapsed,
                estimate.inputTokens(), estimate.outputTokens(),
                model != null ? model : "gpt-4.1"
            ));
        }

        public void recordCached(String inputText, String outputText) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            var estimate = publisher.tokenEstimator.estimate(inputText, outputText);

            publisher.recordInteraction(new InteractionEvent(
                tenantId, conversationId, AgentType.ORCHESTRATOR,
                null, "Cache hit", 1.0, true, elapsed,
                estimate.inputTokens(), estimate.outputTokens(), null
            ));
        }
    }
}
