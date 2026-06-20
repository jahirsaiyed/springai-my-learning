package com.example.agents.observability;

import com.example.agents.AgentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records agent decisions and token usage for observability.
 * Tracks which agent handled the request, which tools were used,
 * routing confidence, and response timing.
 */
@Component
public class AgentDecisionTracker {

    private static final Logger log = LoggerFactory.getLogger(AgentDecisionTracker.class);

    private final JdbcTemplate jdbcTemplate;

    public AgentDecisionTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void trackDecision(UUID conversationId, String agentType,
                               String toolUsed, String reasoning) {
        trackDecision(conversationId, agentType, toolUsed, reasoning, 0.0, 0L);
    }

    public void trackDecision(UUID conversationId, String agentType,
                               String toolUsed, String reasoning,
                               double confidence, long responseTimeMs) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO agent_decisions (id, conversation_id, agent_type, tool_used, reasoning, confidence, response_time_ms)
                VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, ?)
                """,
                conversationId.toString(),
                agentType,
                toolUsed,
                reasoning,
                confidence,
                responseTimeMs
            );
        } catch (Exception e) {
            log.debug("Failed to track agent decision: {}", e.getMessage());
        }
    }

    public void trackTokenUsage(UUID tenantId, UUID conversationId,
                                 String model, int inputTokens, int outputTokens) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO token_usage (id, tenant_id, conversation_id, model, input_tokens, output_tokens)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?, ?)
                """,
                tenantId.toString(),
                conversationId.toString(),
                model,
                inputTokens,
                outputTokens
            );
        } catch (Exception e) {
            log.debug("Failed to track token usage: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getRecentDecisions(UUID conversationId, int limit) {
        try {
            return jdbcTemplate.queryForList(
                """
                SELECT agent_type, tool_used, reasoning, confidence, response_time_ms, created_at
                FROM agent_decisions
                WHERE conversation_id = ?::uuid
                ORDER BY created_at DESC
                LIMIT ?
                """,
                conversationId.toString(), limit
            );
        } catch (Exception e) {
            log.debug("Failed to query agent decisions: {}", e.getMessage());
            return List.of();
        }
    }
}
