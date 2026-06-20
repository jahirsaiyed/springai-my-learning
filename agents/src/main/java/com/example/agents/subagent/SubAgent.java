package com.example.agents.subagent;

import com.example.agents.AgentContext;
import com.example.agents.AgentResponse;
import com.example.agents.AgentType;

/**
 * Interface for all subagents. Each subagent handles a specific domain
 * of customer support (orders, refunds, knowledge, escalation).
 */
public interface SubAgent {

    AgentType getType();

    AgentResponse handle(String userMessage, AgentContext context);
}
