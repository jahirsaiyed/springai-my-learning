package com.example.agents.orchestrator;

import com.example.agents.AgentContext;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Streaming variant of the OrchestratorAgent.
 * Returns Flux<String> for SSE/WebSocket streaming instead of blocking responses.
 */
@Service
public class StreamingOrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(StreamingOrchestratorAgent.class);

    private final IntentClassifier intentClassifier;
    private final ConversationGuardrails guardrails;
    private final MemoryManager memoryManager;
    private final SemanticCacheService cacheService;
    private final ObservabilityEventPublisher observability;
    private final ChatClient.Builder chatClientBuilder;
    private final Map<AgentType, SubAgent> subAgents;

    public StreamingOrchestratorAgent(IntentClassifier intentClassifier,
                                       ConversationGuardrails guardrails,
                                       MemoryManager memoryManager,
                                       SemanticCacheService cacheService,
                                       ObservabilityEventPublisher observability,
                                       ChatClient.Builder chatClientBuilder,
                                       List<SubAgent> subAgentList) {
        this.intentClassifier = intentClassifier;
        this.guardrails = guardrails;
        this.memoryManager = memoryManager;
        this.cacheService = cacheService;
        this.observability = observability;
        this.chatClientBuilder = chatClientBuilder;
        this.subAgents = subAgentList.stream()
            .collect(Collectors.toMap(SubAgent::getType, a -> a));
    }

    /**
     * Start a new conversation with streaming response.
     */
    public Flux<StreamEvent> startConversationStream(UUID tenantId, UUID customerId,
                                                      Channel channel, String userMessage) {
        Conversation conversation = memoryManager.episodic()
            .startConversation(tenantId, customerId, channel);

        return processMessageStream(tenantId, customerId, conversation.getId(), userMessage, 1);
    }

    /**
     * Continue a conversation with streaming response.
     */
    public Flux<StreamEvent> continueConversationStream(UUID tenantId, UUID customerId,
                                                         UUID conversationId, String userMessage) {
        var messages = memoryManager.episodic().getRecentMessages(conversationId, 100);
        int turnCount = messages.size() / 2 + 1;

        return processMessageStream(tenantId, customerId, conversationId, userMessage, turnCount);
    }

    private Flux<StreamEvent> processMessageStream(UUID tenantId, UUID customerId,
                                                     UUID conversationId, String userMessage,
                                                     int turnCount) {
        var timer = observability.startTimer(tenantId, conversationId);

        // Record user message
        memoryManager.episodic().addMessage(conversationId, MessageRole.USER, userMessage);

        // Guardrails
        if (guardrails.isOffTopic(userMessage)) {
            String rejection = guardrails.getOffTopicMessage();
            memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, rejection);
            timer.record(AgentType.ORCHESTRATOR, null, "Off-topic rejection", 1.0, false,
                userMessage, rejection, null);
            return Flux.just(
                StreamEvent.agentSelected(AgentType.ORCHESTRATOR),
                StreamEvent.token(rejection),
                StreamEvent.done(conversationId)
            );
        }

        if (guardrails.shouldForceEscalation(turnCount, AgentContext.MAX_TURNS_BEFORE_ESCALATION)) {
            String msg = guardrails.getForceEscalationMessage();
            memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, msg);
            timer.record(AgentType.ESCALATION, null, "Force escalation at turn " + turnCount,
                1.0, false, userMessage, msg, null);
            return Flux.just(
                StreamEvent.agentSelected(AgentType.ESCALATION),
                StreamEvent.token(msg),
                StreamEvent.done(conversationId)
            );
        }

        // Check cache
        Optional<CachedResponse> cached = cacheService.lookup(tenantId, userMessage);
        if (cached.isPresent()) {
            String response = cached.get().response();
            memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, response);
            timer.recordCached(userMessage, response);
            return Flux.just(
                StreamEvent.cached(cached.get().layer().name()),
                StreamEvent.token(response),
                StreamEvent.done(conversationId)
            );
        }

        // Classify and build context
        IntentClassification intent = intentClassifier.classify(userMessage);
        String memoryContext = memoryManager.buildContext(tenantId, customerId, userMessage);

        // Build the streaming prompt
        AgentType targetType = intent.targetAgent();
        SubAgent targetAgent = subAgents.getOrDefault(targetType, subAgents.get(AgentType.KNOWLEDGE));

        String systemPrompt = getSystemPromptForAgent(targetType);
        String fullPrompt = buildFullPrompt(userMessage, memoryContext, customerId);

        // Collect streamed tokens for observability
        StringBuilder responseCollector = new StringBuilder();

        return Flux.concat(
            Flux.just(StreamEvent.agentSelected(targetType)),
            chatClientBuilder
                .defaultSystem(systemPrompt)
                .build()
                .prompt()
                .user(fullPrompt)
                .stream()
                .content()
                .doOnNext(responseCollector::append)
                .map(StreamEvent::token)
                .doOnComplete(() -> {
                    String fullResponse = responseCollector.toString();
                    memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, fullResponse);
                    cacheService.storeResponse(tenantId, userMessage, fullResponse);
                    timer.record(targetType, null, intent.reasoning(), intent.confidence(),
                        false, fullPrompt, fullResponse, null);
                    log.debug("Stream completed for conversation {}", conversationId);
                }),
            Flux.just(StreamEvent.done(conversationId))
        );
    }

    private String getSystemPromptForAgent(AgentType type) {
        return switch (type) {
            case ORDER -> """
                You are an Order Support Agent. Help with order lookups, tracking, and cancellations.
                Be precise with order details. Confirm before destructive actions.
                """;
            case REFUND -> """
                You are a Refund Support Agent. Help with refund eligibility, processing, and status.
                Always check eligibility first. Confirm amounts before processing.
                """;
            case KNOWLEDGE -> """
                You are a Knowledge Support Agent. Answer policy questions, FAQs, and product info.
                Only use information from the provided context. Don't make up answers.
                """;
            case ESCALATION -> """
                You are an Escalation Agent. Create tickets, transfer to humans, schedule callbacks.
                Gather issue summary, set priority, route to correct department.
                """;
            default -> "You are a helpful customer support assistant.";
        };
    }

    private String buildFullPrompt(String userMessage, String memoryContext, UUID customerId) {
        StringBuilder prompt = new StringBuilder();
        if (!memoryContext.isBlank()) {
            prompt.append("Context:\n").append(memoryContext).append("\n\n");
        }
        prompt.append("Customer ID: ").append(customerId).append("\n");
        prompt.append("Customer message: ").append(userMessage);
        return prompt.toString();
    }

    /**
     * Events sent during streaming.
     */
    public record StreamEvent(
        String type,
        String data
    ) {
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
