package com.example.agents.orchestrator;

import com.example.agents.AgentContext;
import com.example.agents.AgentResponse;
import com.example.agents.AgentType;
import com.example.agents.guardrails.ConversationGuardrails;
import com.example.agents.observability.ObservabilityEventPublisher;
import com.example.agents.subagent.SubAgent;
import com.example.memory.MemoryManager;
import com.example.memory.cache.SemanticCacheService;
import com.example.memory.cache.SemanticCacheService.CachedResponse;
import com.example.memory.episodic.Channel;
import com.example.memory.episodic.Conversation;
import com.example.memory.episodic.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Orchestrator Agent coordinates the entire support conversation:
 *
 * 1. Apply guardrails (off-topic rejection, turn limits)
 * 2. Check semantic cache for cached responses
 * 3. Build memory context (episodic recall, semantic search, shared insights)
 * 4. Classify intent and route to the appropriate subagent
 * 5. Record the interaction in episodic memory
 * 6. Cache the response for future queries
 * 7. Propose insights for shared memory when conversations resolve
 */
@Service
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final IntentClassifier intentClassifier;
    private final ConversationGuardrails guardrails;
    private final MemoryManager memoryManager;
    private final SemanticCacheService cacheService;
    private final ObservabilityEventPublisher observability;
    private final Map<AgentType, SubAgent> subAgents;

    public OrchestratorAgent(IntentClassifier intentClassifier,
                              ConversationGuardrails guardrails,
                              MemoryManager memoryManager,
                              SemanticCacheService cacheService,
                              ObservabilityEventPublisher observability,
                              List<SubAgent> subAgentList) {
        this.intentClassifier = intentClassifier;
        this.guardrails = guardrails;
        this.memoryManager = memoryManager;
        this.cacheService = cacheService;
        this.observability = observability;
        this.subAgents = subAgentList.stream()
            .collect(Collectors.toMap(SubAgent::getType, agent -> agent));
    }

    /**
     * Process a new conversation message (first message).
     */
    public AgentResponse startConversation(UUID tenantId, UUID customerId,
                                            Channel channel, String userMessage) {
        Conversation conversation = memoryManager.episodic()
            .startConversation(tenantId, customerId, channel);

        return processMessage(tenantId, customerId, conversation.getId(), userMessage, 1);
    }

    /**
     * Process a message in an existing conversation.
     */
    public AgentResponse continueConversation(UUID tenantId, UUID customerId,
                                               UUID conversationId, String userMessage) {
        // Count existing messages to track turns
        var messages = memoryManager.episodic().getRecentMessages(conversationId, 100);
        int turnCount = messages.size() / 2 + 1; // Approximate user turns

        return processMessage(tenantId, customerId, conversationId, userMessage, turnCount);
    }

    private AgentResponse processMessage(UUID tenantId, UUID customerId,
                                          UUID conversationId, String userMessage,
                                          int turnCount) {
        var timer = observability.startTimer(tenantId, conversationId);

        // 1. Record user message
        memoryManager.episodic().addMessage(conversationId, MessageRole.USER, userMessage);

        // 2. Apply guardrails
        if (guardrails.isOffTopic(userMessage)) {
            String rejection = guardrails.getOffTopicMessage();
            memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, rejection);
            timer.record(AgentType.ORCHESTRATOR, null, "Off-topic rejection", 1.0, false,
                userMessage, rejection, null);
            return AgentResponse.of(rejection, AgentType.ORCHESTRATOR);
        }

        if (guardrails.shouldForceEscalation(turnCount, AgentContext.MAX_TURNS_BEFORE_ESCALATION)) {
            log.info("Force escalation for conversation {} (turn {})", conversationId, turnCount);
            String escalationMsg = guardrails.getForceEscalationMessage();
            AgentContext ctx = buildContext(tenantId, customerId, conversationId, userMessage, turnCount);
            SubAgent escalationAgent = subAgents.get(AgentType.ESCALATION);
            if (escalationAgent != null) {
                AgentResponse escalation = escalationAgent.handle(escalationMsg, ctx);
                memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, escalation.message());
                timer.record(AgentType.ESCALATION, null, "Force escalation at turn " + turnCount,
                    1.0, false, userMessage, escalation.message(), null);
                return escalation;
            }
            return AgentResponse.of(escalationMsg, AgentType.ORCHESTRATOR);
        }

        // 3. Check semantic cache
        Optional<CachedResponse> cached = cacheService.lookup(tenantId, userMessage);
        if (cached.isPresent()) {
            log.info("Serving cached response (layer: {}) for conversation {}",
                cached.get().layer(), conversationId);
            String response = cached.get().response();
            memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, response);
            timer.recordCached(userMessage, response);
            return AgentResponse.of(response, AgentType.ORCHESTRATOR);
        }

        // 4. Build memory context
        AgentContext agentContext = buildContext(tenantId, customerId, conversationId, userMessage, turnCount);

        // 5. Classify intent and route
        IntentClassification intent = intentClassifier.classify(userMessage);
        log.info("Intent classified: {} (confidence: {}, reason: {})",
            intent.targetAgent(), intent.confidence(), intent.reasoning());

        SubAgent targetAgent = subAgents.get(intent.targetAgent());
        if (targetAgent == null) {
            targetAgent = subAgents.get(AgentType.KNOWLEDGE); // Fallback
        }

        // 6. Execute subagent
        AgentResponse response = targetAgent.handle(userMessage, agentContext);

        // 7. Record assistant response
        memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, response.message());

        // 8. Cache the response (skip for escalations and confirmations)
        if (!response.escalated() && !response.requiresConfirmation()) {
            cacheService.storeResponse(tenantId, userMessage, response.message());
        }

        // 9. Record observability
        timer.record(intent.targetAgent(), null, intent.reasoning(),
            intent.confidence(), false, userMessage, response.message(), null);

        return response;
    }

    /**
     * Close a conversation and optionally propose an insight for shared memory.
     */
    public void resolveConversation(UUID tenantId, UUID conversationId, String resolution) {
        // Generate summary
        var conversation = memoryManager.episodic().getConversation(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        String summary = memoryManager.recall().generateSummary(conversation);
        memoryManager.episodic().closeConversation(conversationId, summary);

        // Propose insight for shared memory if resolution is meaningful
        if (resolution != null && !resolution.isBlank()) {
            memoryManager.shared().proposeInsight(tenantId, conversationId, resolution);
        }

        log.info("Resolved conversation {} with summary", conversationId);
    }

    private AgentContext buildContext(UUID tenantId, UUID customerId,
                                      UUID conversationId, String query, int turnCount) {
        String memoryContext = memoryManager.buildContext(tenantId, customerId, query);
        return new AgentContext(tenantId, customerId, conversationId, memoryContext, turnCount);
    }
}
