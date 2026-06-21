package com.example.agents.orchestrator;

import com.example.agents.AgentResponse;
import com.example.agents.AgentType;
import com.example.agents.graph.SupportGraph;
import com.example.memory.MemoryManager;
import com.example.memory.episodic.Channel;
import com.example.memory.episodic.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The Orchestrator Agent delegates to the LangGraph4j SupportGraph
 * which handles conversation flow, memory, caching, and routing.
 *
 * This class preserves the existing API contract (startConversation,
 * continueConversation, resolveConversation) while delegating the
 * actual processing to the graph.
 */
@Service
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final SupportGraph supportGraph;
    private final MemoryManager memoryManager;

    public OrchestratorAgent(SupportGraph supportGraph,
                              MemoryManager memoryManager) {
        this.supportGraph = supportGraph;
        this.memoryManager = memoryManager;
    }

    /**
     * Process a new conversation message (first message).
     */
    public AgentResponse startConversation(UUID tenantId, UUID customerId,
                                            Channel channel, String userMessage,
                                            String ecomCustomerId) {
        Conversation conversation = memoryManager.episodic()
            .startConversation(tenantId, customerId, channel);

        AgentResponse response = processMessage(tenantId, customerId, conversation.getId(), userMessage, 1, ecomCustomerId);

        // Always include conversationId in the response so clients can continue the conversation
        var metadata = new java.util.HashMap<>(response.metadata());
        metadata.put("conversationId", conversation.getId().toString());
        return new AgentResponse(response.message(), response.handledBy(),
                response.requiresConfirmation(), response.escalated(), metadata);
    }

    /**
     * Process a message in an existing conversation.
     */
    public AgentResponse continueConversation(UUID tenantId, UUID customerId,
                                               UUID conversationId, String userMessage,
                                               String ecomCustomerId) {
        var messages = memoryManager.episodic().getRecentMessages(conversationId, 100);
        int turnCount = messages.size() / 2 + 1;

        AgentResponse response = processMessage(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);

        var metadata = new java.util.HashMap<>(response.metadata());
        metadata.put("conversationId", conversationId.toString());
        return new AgentResponse(response.message(), response.handledBy(),
                response.requiresConfirmation(), response.escalated(), metadata);
    }

    private AgentResponse processMessage(UUID tenantId, UUID customerId,
                                          UUID conversationId, String userMessage,
                                          int turnCount, String ecomCustomerId) {
        var result = supportGraph.invoke(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);

        AgentType handledBy;
        try {
            handledBy = AgentType.valueOf(result.handledBy());
        } catch (IllegalArgumentException e) {
            handledBy = AgentType.ORCHESTRATOR;
        }

        if (result.escalated()) {
            return AgentResponse.escalated(result.message(),
                java.util.Map.of("conversationId", conversationId.toString()));
        }

        if (result.requiresConfirmation()) {
            return AgentResponse.withConfirmation(result.message(), handledBy, java.util.Map.of());
        }

        return AgentResponse.of(result.message(), handledBy);
    }

    /**
     * Close a conversation and optionally propose an insight for shared memory.
     */
    public void resolveConversation(UUID tenantId, UUID conversationId, String resolution) {
        var conversation = memoryManager.episodic().getConversation(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        String summary = memoryManager.recall().generateSummary(conversation);
        memoryManager.episodic().closeConversation(conversationId, summary);

        if (resolution != null && !resolution.isBlank()) {
            memoryManager.shared().proposeInsight(tenantId, conversationId, resolution);
        }

        log.info("Resolved conversation {} with summary", conversationId);
    }
}
