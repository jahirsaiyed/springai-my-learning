package com.example.agents.guardrails;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Enforces conversation boundaries:
 * - Rejects off-topic queries
 * - Requires confirmation for destructive actions
 * - Forces escalation after max turns
 */
@Component
public class ConversationGuardrails {

    private static final Set<String> OFF_TOPIC_INDICATORS = Set.of(
        "write me a poem", "tell me a joke", "write code",
        "help me with homework", "what is the meaning of life",
        "play a game", "sing a song", "recipe for"
    );

    private static final Set<String> DESTRUCTIVE_ACTIONS = Set.of(
        "cancel", "delete", "remove", "refund"
    );

    /**
     * Check if a query is off-topic for customer support.
     */
    public boolean isOffTopic(String query) {
        String lower = query.toLowerCase().trim();
        return OFF_TOPIC_INDICATORS.stream().anyMatch(lower::contains);
    }

    /**
     * Get the off-topic rejection message.
     */
    public String getOffTopicMessage() {
        return "I'm a customer support assistant and can help you with orders, "
            + "refunds, tracking, and product questions. "
            + "Could you please ask me something related to your account or orders?";
    }

    /**
     * Check if the user's intent involves a destructive/irreversible action
     * that requires explicit confirmation.
     */
    public boolean requiresConfirmation(String query) {
        String lower = query.toLowerCase();
        return DESTRUCTIVE_ACTIONS.stream().anyMatch(lower::contains);
    }

    /**
     * Check if conversation has exceeded max turns and should be escalated.
     */
    public boolean shouldForceEscalation(int turnCount, int maxTurns) {
        return turnCount >= maxTurns;
    }

    public String getForceEscalationMessage() {
        return "It seems we haven't been able to resolve your issue. "
            + "Let me connect you with a human agent who can help further.";
    }
}
