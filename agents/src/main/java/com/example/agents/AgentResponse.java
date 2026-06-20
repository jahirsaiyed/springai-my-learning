package com.example.agents;

import java.util.Map;

/**
 * Standardized response from any subagent.
 */
public record AgentResponse(
    String message,
    AgentType handledBy,
    boolean requiresConfirmation,
    boolean escalated,
    Map<String, Object> metadata
) {

    public static AgentResponse of(String message, AgentType agent) {
        return new AgentResponse(message, agent, false, false, Map.of());
    }

    public static AgentResponse withConfirmation(String message, AgentType agent, Map<String, Object> metadata) {
        return new AgentResponse(message, agent, true, false, metadata);
    }

    public static AgentResponse escalated(String message, Map<String, Object> metadata) {
        return new AgentResponse(message, AgentType.ESCALATION, false, true, metadata);
    }
}
