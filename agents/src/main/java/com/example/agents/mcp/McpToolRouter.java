package com.example.agents.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Routes MCP tool callbacks to specific agents based on tool name.
 * Only adds tools that don't overlap with existing local agent tools.
 */
@Component
public class McpToolRouter {

    private static final Logger log = LoggerFactory.getLogger(McpToolRouter.class);

    private static final Set<String> ORDER_AGENT_TOOLS = Set.of(
            "getOrder", "listCustomerOrders", "getCustomer", "searchCustomers",
            "trackShipment", "cancelOrder"
    );

    private static final Set<String> REFUND_AGENT_TOOLS = Set.of(
            "checkRefundEligibility", "getRefundStatus"
    );

    private static final Set<String> KNOWLEDGE_AGENT_TOOLS = Set.of(
            "searchProducts", "getProduct", "listCategories",
            "getOrderReviews", "getProductReviews"
    );

    private final Map<String, ToolCallback> mcpTools;
    private final McpResilienceProperties resilienceProps;

    public McpToolRouter(@Autowired(required = false) List<ToolCallbackProvider> providers,
                         McpResilienceProperties resilienceProps) {
        this.resilienceProps = resilienceProps;
        if (providers == null || providers.isEmpty()) {
            log.warn("No MCP tool providers found — MCP tools will not be available");
            this.mcpTools = Map.of();
            return;
        }

        this.mcpTools = providers.stream()
                .flatMap(p -> {
                    try {
                        return Arrays.stream(p.getToolCallbacks());
                    } catch (Exception e) {
                        log.warn("Failed to get tool callbacks from provider: {}", e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toMap(
                        cb -> cb.getToolDefinition().name(),
                        cb -> cb,
                        (a, b) -> a
                ));

        log.info("MCP tools available: {}", mcpTools.keySet());
    }

    public ToolCallback[] getOrderAgentTools() {
        return filterByNames(ORDER_AGENT_TOOLS);
    }

    public ToolCallback[] getRefundAgentTools() {
        return filterByNames(REFUND_AGENT_TOOLS);
    }

    public ToolCallback[] getKnowledgeAgentTools() {
        return filterByNames(KNOWLEDGE_AGENT_TOOLS);
    }

    public boolean hasTools() {
        return !mcpTools.isEmpty();
    }

    private ToolCallback[] filterByNames(Set<String> names) {
        return names.stream()
                .map(mcpTools::get)
                .filter(Objects::nonNull)
                .map(cb -> new ResilientToolCallback(cb, resilienceProps))
                .toArray(ToolCallback[]::new);
    }
}
