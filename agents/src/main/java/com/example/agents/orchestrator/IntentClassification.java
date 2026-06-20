package com.example.agents.orchestrator;

import com.example.agents.AgentType;

/**
 * Result of intent classification by the orchestrator.
 */
public record IntentClassification(
    AgentType targetAgent,
    double confidence,
    String reasoning
) {

    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }
}
