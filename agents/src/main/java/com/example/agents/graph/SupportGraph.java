package com.example.agents.graph;

import com.example.agents.AgentContext;
import com.example.agents.AgentType;
import com.example.agents.guardrails.ConversationGuardrails;
import com.example.agents.orchestrator.IntentClassifier;
import com.example.agents.tools.AskUserQuestionTool;
import com.example.agents.tools.EscalationTools;
import com.example.agents.tools.KnowledgeTools;
import com.example.agents.tools.OrderTools;
import com.example.agents.tools.RefundTools;
import com.example.memory.MemoryManager;
import com.example.memory.cache.SemanticCacheService;
import com.example.memory.cache.SemanticCacheService.CachedResponse;
import com.example.memory.episodic.MessageRole;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j-based support agent graph.
 * Replaces the hand-rolled OrchestratorAgent with a proper state graph
 * that automatically accumulates conversation messages across turns.
 */
@Service
public class SupportGraph {

    private static final Logger log = LoggerFactory.getLogger(SupportGraph.class);

    private static final String GUARDRAILS = "guardrails";
    private static final String CACHE_CHECK = "cache_check";
    private static final String BUILD_CONTEXT = "build_context";
    private static final String ROUTER = "router";
    private static final String ORDER_AGENT = "order_agent";
    private static final String REFUND_AGENT = "refund_agent";
    private static final String KNOWLEDGE_AGENT = "knowledge_agent";
    private static final String ESCALATION_AGENT = "escalation_agent";
    private static final String POST_PROCESS = "post_process";

    private final ConversationGuardrails guardrails;
    private final MemoryManager memoryManager;
    private final SemanticCacheService cacheService;
    private final IntentClassifier intentClassifier;
    private final ChatClient orderClient;
    private final ChatClient refundClient;
    private final ChatClient knowledgeClient;
    private final ChatClient escalationClient;

    private final CompiledGraph<SupportGraphState> compiledGraph;
    private final MemorySaver memorySaver;

    public SupportGraph(ConversationGuardrails guardrails,
                        MemoryManager memoryManager,
                        SemanticCacheService cacheService,
                        IntentClassifier intentClassifier,
                        ChatClient.Builder chatClientBuilder,
                        OrderTools orderTools,
                        RefundTools refundTools,
                        KnowledgeTools knowledgeTools,
                        EscalationTools escalationTools,
                        AskUserQuestionTool askUserQuestionTool) {
        this.guardrails = guardrails;
        this.memoryManager = memoryManager;
        this.cacheService = cacheService;
        this.intentClassifier = intentClassifier;

        // Build dedicated ChatClients for each agent domain with their tools
        this.orderClient = chatClientBuilder.clone()
            .defaultSystem("""
                You are an Order Support Agent for an e-commerce platform.
                Help with order lookups, tracking, shipment status, delivery questions, and order cancellations.
                Be precise with order details. Always confirm before destructive actions.
                Use the conversation history to maintain context across turns.

                CRITICAL RULES:
                - NEVER fabricate or invent order details. Only report information returned by your tools.
                - If a tool returns ORDER_NOT_FOUND, tell the user that no order was found with that ID.
                  Ask them to double-check their order ID (e.g., from their confirmation email) and try again.
                - If a tool returns NO_SHIPMENT, explain that the order hasn't shipped yet and tracking is not available.
                - Do NOT guess or make up shipping carriers, tracking numbers, delivery dates, or order contents.
                """)
            .defaultTools(orderTools, askUserQuestionTool)
            .build();

        this.refundClient = chatClientBuilder.clone()
            .defaultSystem("""
                You are a Refund Support Agent for an e-commerce platform.
                Help with refund eligibility, processing refund requests, checking refund status, and explaining return policies.
                Always check eligibility first. Confirm amounts before processing. Never process without explicit confirmation.
                Use the conversation history to maintain context across turns.
                """)
            .defaultTools(refundTools, askUserQuestionTool)
            .build();

        this.knowledgeClient = chatClientBuilder.clone()
            .defaultSystem("""
                You are a Knowledge Support Agent for an e-commerce platform.
                Answer questions about policies, FAQs, product information, and general inquiries.
                Use the provided context and conversation history to give accurate, helpful answers.
                If the customer shared personal information earlier in the conversation, remember and use it.
                """)
            .defaultTools(knowledgeTools, askUserQuestionTool)
            .build();

        this.escalationClient = chatClientBuilder.clone()
            .defaultSystem("""
                You are an Escalation Agent for an e-commerce platform.
                Create support tickets, transfer to human agents, schedule callbacks, and triage issues.
                Gather a clear summary, set priority, and route to the correct department.
                Use the conversation history to maintain context across turns.
                """)
            .defaultTools(escalationTools, askUserQuestionTool)
            .build();

        this.memorySaver = new MemorySaver();

        try {
            this.compiledGraph = buildGraph();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build support graph", e);
        }
    }

    private CompiledGraph<SupportGraphState> buildGraph() throws Exception {
        var stateSerializer = new ObjectStreamStateSerializer<>(SupportGraphState::new);
        var messageSerializer = new SpringAiMessageSerializer();
        stateSerializer.mapper().register(UserMessage.class, messageSerializer);
        stateSerializer.mapper().register(AssistantMessage.class, messageSerializer);

        var stateGraph = new StateGraph<>(
            SupportGraphState.SCHEMA,
            stateSerializer
        );

        // Add nodes
        stateGraph
            .addNode(GUARDRAILS, node_async(this::guardrailsNode))
            .addNode(CACHE_CHECK, node_async(this::cacheCheckNode))
            .addNode(BUILD_CONTEXT, node_async(this::buildContextNode))
            .addNode(ROUTER, node_async(this::routerNode))
            .addNode(ORDER_AGENT, node_async(this::orderAgentNode))
            .addNode(REFUND_AGENT, node_async(this::refundAgentNode))
            .addNode(KNOWLEDGE_AGENT, node_async(this::knowledgeAgentNode))
            .addNode(ESCALATION_AGENT, node_async(this::escalationAgentNode))
            .addNode(POST_PROCESS, node_async(this::postProcessNode));

        // Add edges
        stateGraph.addEdge(START, GUARDRAILS);

        // Guardrails → conditional: reject/escalate → post_process, pass → cache_check
        stateGraph.addConditionalEdges(GUARDRAILS,
            edge_async(this::afterGuardrails),
            Map.of(
                "pass", CACHE_CHECK,
                "reject", POST_PROCESS,
                "escalate", ESCALATION_AGENT
            )
        );

        // Cache → conditional: hit → post_process, miss → build_context
        stateGraph.addConditionalEdges(CACHE_CHECK,
            edge_async(this::afterCacheCheck),
            Map.of(
                "hit", POST_PROCESS,
                "miss", BUILD_CONTEXT
            )
        );

        stateGraph.addEdge(BUILD_CONTEXT, ROUTER);

        // Router → conditional based on intent
        stateGraph.addConditionalEdges(ROUTER,
            edge_async(this::afterRouter),
            Map.of(
                "ORDER", ORDER_AGENT,
                "REFUND", REFUND_AGENT,
                "KNOWLEDGE", KNOWLEDGE_AGENT,
                "ESCALATION", ESCALATION_AGENT
            )
        );

        // All agents → post_process
        stateGraph.addEdge(ORDER_AGENT, POST_PROCESS);
        stateGraph.addEdge(REFUND_AGENT, POST_PROCESS);
        stateGraph.addEdge(KNOWLEDGE_AGENT, POST_PROCESS);
        stateGraph.addEdge(ESCALATION_AGENT, POST_PROCESS);

        stateGraph.addEdge(POST_PROCESS, END);

        return stateGraph.compile(
            CompileConfig.builder()
                .checkpointSaver(memorySaver)
                .build()
        );
    }

    // ─── Public API ──────────────────────────────────────────────────

    /**
     * Invoke the graph for a conversation turn.
     * The conversationId is used as the thread ID for MemorySaver checkpointing,
     * so messages automatically accumulate across turns.
     */
    public GraphResult invoke(UUID tenantId, UUID customerId, UUID conversationId,
                              String userMessage, int turnCount, String ecomCustomerId) {
        var config = RunnableConfig.builder()
            .threadId(conversationId.toString())
            .build();

        var inputs = new HashMap<String, Object>();
        inputs.put("messages", new UserMessage(userMessage));
        inputs.put(SupportGraphState.TENANT_ID, tenantId);
        inputs.put(SupportGraphState.CUSTOMER_ID, customerId);
        inputs.put(SupportGraphState.CONVERSATION_ID, conversationId);
        inputs.put(SupportGraphState.TURN_COUNT, turnCount);
        if (ecomCustomerId != null) {
            inputs.put(SupportGraphState.ECOM_CUSTOMER_ID, ecomCustomerId);
        }

        try {
            var result = compiledGraph.invoke(inputs, config);
            return result.map(state -> new GraphResult(
                state.responseText(),
                state.handledBy(),
                false,
                "ESCALATION".equals(state.handledBy())
            )).orElse(new GraphResult(
                "I'm sorry, I couldn't process your request. Please try again.",
                "ORCHESTRATOR", false, false
            ));
        } catch (Exception e) {
            log.error("Graph execution failed for conversation {}", conversationId, e);
            return new GraphResult(
                "I'm sorry, an error occurred. Please try again.",
                "ORCHESTRATOR", false, false
            );
        }
    }

    public record GraphResult(
        String message,
        String handledBy,
        boolean requiresConfirmation,
        boolean escalated
    ) {}

    // ─── Node Implementations ────────────────────────────────────────

    private Map<String, Object> guardrailsNode(SupportGraphState state) {
        var lastMsg = getLastUserMessageText(state);
        int turnCount = state.turnCount();

        if (guardrails.isOffTopic(lastMsg)) {
            String rejection = guardrails.getOffTopicMessage();
            return Map.of(
                SupportGraphState.GUARDRAIL_RESULT, "reject",
                SupportGraphState.RESPONSE_TEXT, rejection,
                SupportGraphState.HANDLED_BY, "ORCHESTRATOR",
                "messages", new AssistantMessage(rejection)
            );
        }

        if (guardrails.shouldForceEscalation(turnCount, AgentContext.MAX_TURNS_BEFORE_ESCALATION)) {
            String escalationMsg = guardrails.getForceEscalationMessage();
            return Map.of(
                SupportGraphState.GUARDRAIL_RESULT, "escalate",
                SupportGraphState.RESPONSE_TEXT, escalationMsg,
                SupportGraphState.HANDLED_BY, "ESCALATION"
            );
        }

        return Map.of(SupportGraphState.GUARDRAIL_RESULT, "pass");
    }

    private String afterGuardrails(SupportGraphState state) {
        return state.guardrailResult();
    }

    private Map<String, Object> cacheCheckNode(SupportGraphState state) {
        var lastMsg = getLastUserMessageText(state);
        UUID tenantId = state.tenantId();

        Optional<CachedResponse> cached = cacheService.lookup(tenantId, lastMsg);
        if (cached.isPresent()) {
            log.info("Cache hit (layer: {}) for conversation {}",
                cached.get().layer(), state.conversationId());
            return Map.of(
                SupportGraphState.CACHED_RESPONSE, cached.get().response(),
                SupportGraphState.RESPONSE_TEXT, cached.get().response(),
                SupportGraphState.HANDLED_BY, "ORCHESTRATOR",
                "messages", new AssistantMessage(cached.get().response())
            );
        }

        return Map.of();
    }

    private String afterCacheCheck(SupportGraphState state) {
        return state.cachedResponse().isPresent() ? "hit" : "miss";
    }

    private Map<String, Object> buildContextNode(SupportGraphState state) {
        String context = memoryManager.buildContext(
            state.tenantId(), state.customerId(), getLastUserMessageText(state));
        return Map.of(SupportGraphState.MEMORY_CONTEXT, context);
    }

    private Map<String, Object> routerNode(SupportGraphState state) {
        var lastMsg = getLastUserMessageText(state);
        var intent = intentClassifier.classify(lastMsg);
        log.info("Intent classified: {} (confidence: {})", intent.targetAgent(), intent.confidence());
        return Map.of(SupportGraphState.INTENT, intent.targetAgent().name());
    }

    private String afterRouter(SupportGraphState state) {
        return state.intent();
    }

    private Map<String, Object> orderAgentNode(SupportGraphState state) {
        return callAgent(orderClient, state, "ORDER");
    }

    private Map<String, Object> refundAgentNode(SupportGraphState state) {
        return callAgent(refundClient, state, "REFUND");
    }

    private Map<String, Object> knowledgeAgentNode(SupportGraphState state) {
        return callAgent(knowledgeClient, state, "KNOWLEDGE");
    }

    private Map<String, Object> escalationAgentNode(SupportGraphState state) {
        return callAgent(escalationClient, state, "ESCALATION");
    }

    private Map<String, Object> callAgent(ChatClient client, SupportGraphState state, String agentName) {
        // Build the message list: conversation history from graph state
        var messages = state.messages();

        // Create a Prompt directly with the full message list so the ChatClient's
        // defaultSystem prompt is included automatically
        var prompt = client.prompt();
        if (messages != null && !messages.isEmpty()) {
            prompt.messages(messages);
        }

        // Inject ecommerce customer context as a user-level context message
        // (NOT prompt.system() which overwrites the defaultSystem prompt)
        String ecomCustId = state.ecomCustomerId();
        if (ecomCustId != null) {
            prompt.messages(new UserMessage(
                    "[SYSTEM CONTEXT - DO NOT REPEAT TO USER] "
                    + "The current user's ecommerce customer ID is: " + ecomCustId
                    + ". Use this ID when calling getRecentOrders or other order/refund tools."));
        } else if ("ORDER".equals(agentName) || "REFUND".equals(agentName)) {
            prompt.messages(new UserMessage(
                    "[SYSTEM CONTEXT - DO NOT REPEAT TO USER] "
                    + "This user has no linked ecommerce account. "
                    + "If they ask about orders or refunds, inform them that their account is not linked "
                    + "and suggest they contact an admin to link their account."));
        }

        String response = prompt.call().content();

        return Map.of(
            SupportGraphState.RESPONSE_TEXT, response,
            SupportGraphState.HANDLED_BY, agentName,
            "messages", new AssistantMessage(response)
        );
    }

    private Map<String, Object> postProcessNode(SupportGraphState state) {
        UUID conversationId = state.conversationId();
        UUID tenantId = state.tenantId();

        // Record messages in episodic memory
        String lastUserMsg = getLastUserMessageText(state);
        String responseText = state.responseText();

        memoryManager.episodic().addMessage(conversationId, MessageRole.USER, lastUserMsg);
        memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, responseText);

        // Cache response (skip for escalations)
        if (!"ESCALATION".equals(state.handledBy()) && state.cachedResponse().isEmpty()) {
            cacheService.storeResponse(tenantId, lastUserMsg, responseText);
        }

        return Map.of();
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private String getLastUserMessageText(SupportGraphState state) {
        var messages = state.messages();
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                var msg = messages.get(i);
                if (msg instanceof UserMessage) {
                    return msg.getText();
                }
            }
        }
        return "";
    }
}
