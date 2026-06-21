package com.example.agents.orchestrator;

import com.example.agents.AgentType;
import com.example.agents.graph.SupportGraph;
import com.example.memory.MemoryManager;
import com.example.memory.episodic.Channel;
import com.example.memory.episodic.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Streaming variant of the OrchestratorAgent.
 * Delegates to SupportGraph for processing, then wraps the result as StreamEvents.
 *
 * Note: Full token-level streaming would require graph-level streaming support.
 * For now, this invokes the graph synchronously and emits the result as a single token event.
 */
@Service
public class StreamingOrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(StreamingOrchestratorAgent.class);

    private final SupportGraph supportGraph;
    private final MemoryManager memoryManager;

    public StreamingOrchestratorAgent(SupportGraph supportGraph,
                                       MemoryManager memoryManager) {
        this.supportGraph = supportGraph;
        this.memoryManager = memoryManager;
    }

    public Flux<StreamEvent> startConversationStream(UUID tenantId, UUID customerId,
                                                      Channel channel, String userMessage,
                                                      String ecomCustomerId) {
        Conversation conversation = memoryManager.episodic()
            .startConversation(tenantId, customerId, channel);

        return processStream(tenantId, customerId, conversation.getId(), userMessage, 1, ecomCustomerId);
    }

    public Flux<StreamEvent> continueConversationStream(UUID tenantId, UUID customerId,
                                                         UUID conversationId, String userMessage,
                                                         String ecomCustomerId) {
        var messages = memoryManager.episodic().getRecentMessages(conversationId, 100);
        int turnCount = messages.size() / 2 + 1;

        return processStream(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);
    }

    private Flux<StreamEvent> processStream(UUID tenantId, UUID customerId,
                                             UUID conversationId, String userMessage,
                                             int turnCount, String ecomCustomerId) {
        return Flux.defer(() -> {
            var result = supportGraph.invoke(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);

            AgentType agentType;
            try {
                agentType = AgentType.valueOf(result.handledBy());
            } catch (IllegalArgumentException e) {
                agentType = AgentType.ORCHESTRATOR;
            }

            return Flux.just(
                StreamEvent.agentSelected(agentType),
                StreamEvent.token(result.message()),
                StreamEvent.done(conversationId)
            );
        });
    }

    public record StreamEvent(String type, String data) {
        public static StreamEvent agentSelected(AgentType agent) {
            return new StreamEvent("agent", agent.name());
        }

        public static StreamEvent cached(String layer) {
            return new StreamEvent("cached", layer);
        }

        public static StreamEvent token(String text) {
            return new StreamEvent("token", text);
        }

        public static StreamEvent done(UUID conversationId) {
            return new StreamEvent("done", conversationId.toString());
        }

        public static StreamEvent error(String message) {
            return new StreamEvent("error", message);
        }
    }
}
