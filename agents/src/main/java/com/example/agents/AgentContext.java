package com.example.agents;

import java.util.List;
import java.util.UUID;

/**
 * Context passed to subagents containing tenant, customer, and conversation info.
 */
public record AgentContext(
    UUID tenantId,
    UUID customerId,
    UUID conversationId,
    String memoryContext,
    List<ChatMessage> conversationHistory,
    int turnCount
) {

    /**
     * A lightweight representation of a conversation message for prompt building.
     */
    public record ChatMessage(String role, String content) {}


    public static final int MAX_TURNS_BEFORE_ESCALATION = 15;

    public boolean shouldForceEscalation() {
        return turnCount >= MAX_TURNS_BEFORE_ESCALATION;
    }
}
