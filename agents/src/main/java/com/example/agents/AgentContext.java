package com.example.agents;

import java.util.UUID;

/**
 * Context passed to subagents containing tenant, customer, and conversation info.
 */
public record AgentContext(
    UUID tenantId,
    UUID customerId,
    UUID conversationId,
    String memoryContext,
    int turnCount
) {

    public static final int MAX_TURNS_BEFORE_ESCALATION = 15;

    public boolean shouldForceEscalation() {
        return turnCount >= MAX_TURNS_BEFORE_ESCALATION;
    }
}
