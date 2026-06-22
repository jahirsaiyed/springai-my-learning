package com.example.agents.graph;

import com.example.agents.AgentContext;
import com.example.agents.AgentType;
import com.example.agents.guardrails.ConversationGuardrails;
import com.example.agents.mcp.McpResilienceProperties;
import com.example.agents.mcp.McpToolRouter;
import com.example.agents.mcp.ResilientToolCallback;
import com.example.agents.orchestrator.IntentClassifier;
import com.example.agents.tools.AskUserQuestionTool;
import com.example.agents.tools.EscalationTools;
import com.example.agents.tools.KnowledgeTools;
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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
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
    private final McpResilienceProperties mcpProps;
    private final ChatClient orderClient;
    private final ChatClient refundClient;
    private final ChatClient knowledgeClient;
    private final ChatClient escalationClient;

    private final ToolCallback[] orderMcpTools;
    private final ToolCallback[] refundMcpTools;

    private final String orderSystemPrompt;
    private final String refundSystemPrompt;

    private final CompiledGraph<SupportGraphState> compiledGraph;
    private final MemorySaver memorySaver;

    public SupportGraph(ConversationGuardrails guardrails,
                        MemoryManager memoryManager,
                        SemanticCacheService cacheService,
                        IntentClassifier intentClassifier,
                        ChatModel chatModel,
                        KnowledgeTools knowledgeTools,
                        EscalationTools escalationTools,
                        AskUserQuestionTool askUserQuestionTool,
                        McpToolRouter mcpToolRouter,
                        McpResilienceProperties mcpProps) {
        this.guardrails = guardrails;
        this.memoryManager = memoryManager;
        this.cacheService = cacheService;
        this.intentClassifier = intentClassifier;
        this.mcpProps = mcpProps;

        this.orderMcpTools = mcpToolRouter.getOrderAgentTools();
        this.refundMcpTools = mcpToolRouter.getRefundAgentTools();

        // Store system prompts so callAgent() can rebuild the full prompt with context appended.
        // prompt.system() REPLACES defaultSystem — it does NOT append.
        this.orderSystemPrompt = """
                You are an Order Support Agent for an e-commerce platform.
                Help with order lookups, tracking, shipment status, delivery questions, and order cancellations.
                Be precise with order details. Always confirm before destructive actions.
                Use the conversation history to maintain context across turns.

                TOOL USAGE:
                - When the user asks about "my latest order", "my orders", or "my recent orders" without
                  providing an order ID, use the listCustomerOrders tool with their customer ID to retrieve
                  their orders. Then show the most recent one or the full list as appropriate.
                - Use trackShipment to get delivery dates and shipping info for a specific order.
                - Use getOrder to get full details for a specific order ID.
                - Always call a tool to look up data. NEVER ask the user for information you can look up yourself.

                CRITICAL RULES:
                - NEVER fabricate or invent order details. Only report information returned by your tools.
                - If a tool returns ORDER_NOT_FOUND, tell the user that no order was found with that ID.
                  Ask them to double-check their order ID (e.g., from their confirmation email) and try again.
                - If a tool returns NO_SHIPMENT, explain that the order hasn't shipped yet and tracking is not available.
                - Do NOT guess or make up shipping carriers, tracking numbers, delivery dates, or order contents.
                - If a tool returns MCP_UNAVAILABLE, inform the user that the order system is temporarily
                  unavailable and suggest they try again shortly or contact support for urgent issues.
                """;

        this.refundSystemPrompt = """
                You are a Refund Support Agent for an e-commerce platform.
                Help with refund eligibility, processing refund requests, checking refund status, and explaining return policies.
                Always check eligibility first. Confirm amounts before processing. Never process without explicit confirmation.
                Use the conversation history to maintain context across turns.

                TOOL USAGE:
                - Use checkRefundEligibility to verify if an order qualifies for a refund before processing.
                - Use getRefundStatus to check the current status of a refund request.
                - Always call a tool to look up data. NEVER ask the user for information you can look up yourself.

                CRITICAL RULES:
                - If a tool returns MCP_UNAVAILABLE, inform the user that the refund system is temporarily
                  unavailable and suggest they try again shortly or contact support for urgent issues.
                """;

        // Build dedicated ChatClients for each agent domain with their tools.
        // Uses ChatClient.builder(chatModel) directly to avoid auto-registered
        // MCP tools from ChatClient.Builder — enables selective tool routing.
        this.orderClient = ChatClient.builder(chatModel)
            .defaultSystem(orderSystemPrompt)
            .defaultTools(askUserQuestionTool)
            .defaultToolCallbacks(orderMcpTools)
            .build();

        this.refundClient = ChatClient.builder(chatModel)
            .defaultSystem(refundSystemPrompt)
            .defaultTools(askUserQuestionTool)
            .defaultToolCallbacks(refundMcpTools)
            .build();

        this.knowledgeClient = ChatClient.builder(chatModel)
            .defaultSystem("""
                You are a Knowledge Support Agent for an e-commerce platform.
                Answer questions about policies, FAQs, product information, and general inquiries.
                Use the provided context and conversation history to give accurate, helpful answers.
                If the customer shared personal information earlier in the conversation, remember and use it.
                """)
            .defaultTools(knowledgeTools, askUserQuestionTool)
            .defaultToolCallbacks(mcpToolRouter.getKnowledgeAgentTools())
            .build();

        this.escalationClient = ChatClient.builder(chatModel)
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

        // ORDER and REFUND agents → conditional: escalate on MCP failures, otherwise post_process
        stateGraph.addConditionalEdges(ORDER_AGENT,
            edge_async(this::afterMcpAgent),
            Map.of(
                "escalate", ESCALATION_AGENT,
                "continue", POST_PROCESS
            )
        );
        stateGraph.addConditionalEdges(REFUND_AGENT,
            edge_async(this::afterMcpAgent),
            Map.of(
                "escalate", ESCALATION_AGENT,
                "continue", POST_PROCESS
            )
        );

        // KNOWLEDGE and ESCALATION agents keep direct edges to post_process
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

        // Skip cache for short/ambiguous messages — they produce false semantic matches
        if (lastMsg.trim().split("\\s+").length <= 3) {
            log.debug("Skipping cache for short message: '{}'", lastMsg);
            return Map.of();
        }

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
        String previousIntent = state.previousIntent();
        var intent = intentClassifier.classify(lastMsg, state.messages(), previousIntent);
        String intentName = intent.targetAgent().name();
        log.info("Intent classified: {} (confidence: {}, previous: {}, reason: {})",
            intentName, intent.confidence(), previousIntent, intent.reasoning());
        return Map.of(
            SupportGraphState.INTENT, intentName,
            SupportGraphState.PREVIOUS_INTENT, intentName
        );
    }

    private String afterRouter(SupportGraphState state) {
        return state.intent();
    }

    private String afterMcpAgent(SupportGraphState state) {
        int failures = state.mcpFailureCount();
        if (failures >= mcpProps.getMaxFailuresBeforeEscalation()) {
            return "escalate";
        }
        return "continue";
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
        var messages = state.messages();

        var prompt = client.prompt();
        if (messages != null && !messages.isEmpty()) {
            prompt.messages(messages);
        }

        // Inject ecommerce customer context by rebuilding the full system prompt.
        // prompt.system() REPLACES defaultSystem — so we must include the original prompt.
        String ecomCustId = state.ecomCustomerId();
        if ("ORDER".equals(agentName) || "REFUND".equals(agentName)) {
            String basePrompt = "ORDER".equals(agentName) ? orderSystemPrompt : refundSystemPrompt;
            if (ecomCustId != null) {
                prompt.system(basePrompt
                    + "\nThe current user's ecommerce customer ID is: " + ecomCustId
                    + ". Use this ID when calling order, customer, or refund tools.");
            } else {
                prompt.system(basePrompt
                    + "\nThis user has no linked ecommerce account. "
                    + "If they ask about orders or refunds, inform them that their account is not linked "
                    + "and suggest they contact an admin to link their account.");
            }
        }

        String response = prompt.call().content();

        int failures = countMcpFailures(agentName);
        if (failures > 0) {
            log.warn("Agent {} recorded {} MCP tool failure(s) this turn", agentName, failures);
            return Map.of(
                SupportGraphState.RESPONSE_TEXT, response,
                SupportGraphState.HANDLED_BY, agentName,
                SupportGraphState.MCP_FAILURE_COUNT, failures,
                "messages", new AssistantMessage(response)
            );
        }

        return Map.of(
            SupportGraphState.RESPONSE_TEXT, response,
            SupportGraphState.HANDLED_BY, agentName,
            "messages", new AssistantMessage(response)
        );
    }

    private int countMcpFailures(String agentName) {
        ToolCallback[] tools = switch (agentName) {
            case "ORDER" -> orderMcpTools;
            case "REFUND" -> refundMcpTools;
            default -> new ToolCallback[0];
        };
        int total = 0;
        for (ToolCallback cb : tools) {
            if (cb instanceof ResilientToolCallback resilient) {
                total += resilient.getConsecutiveFailures();
            }
        }
        return total;
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
