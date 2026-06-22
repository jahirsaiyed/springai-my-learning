package com.example.agents.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("McpToolRouter")
class McpToolRouterTest {

    private McpResilienceProperties resilienceProps;
    private McpToolRouter router;

    @BeforeEach
    void setUp() {
        resilienceProps = new McpResilienceProperties();
    }

    @Test
    @DisplayName("returns ResilientToolCallback[] when providers have matching tools")
    void getOrderAgentTools_returnsResilientCallbacks() {
        var provider = mock(ToolCallbackProvider.class);
        var orderCallback = mockCallback("getOrder");
        var customerCallback = mockCallback("getCustomer");
        var unknownCallback = mockCallback("unknownTool");
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{
                orderCallback, customerCallback, unknownCallback
        });

        router = new McpToolRouter(List.of(provider), resilienceProps);
        ToolCallback[] result = router.getOrderAgentTools();

        assertThat(result).hasSize(2);
        assertThat(result[0]).isInstanceOf(ResilientToolCallback.class);
        assertThat(result[1]).isInstanceOf(ResilientToolCallback.class);
    }

    @Test
    @DisplayName("returns empty array when no providers")
    void getOrderAgentTools_noProviders_returnsEmpty() {
        router = new McpToolRouter(null, resilienceProps);
        ToolCallback[] result = router.getOrderAgentTools();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty array when providers list is empty")
    void getOrderAgentTools_emptyProviderList_returnsEmpty() {
        router = new McpToolRouter(List.of(), resilienceProps);
        ToolCallback[] result = router.getOrderAgentTools();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filters tools correctly for refund agent")
    void getRefundAgentTools_filtersCorrectly() {
        var provider = mock(ToolCallbackProvider.class);
        var refundEligibilityCallback = mockCallback("checkRefundEligibility");
        var refundStatusCallback = mockCallback("getRefundStatus");
        var orderCallback = mockCallback("getOrder");
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{
                refundEligibilityCallback, refundStatusCallback, orderCallback
        });

        router = new McpToolRouter(List.of(provider), resilienceProps);
        ToolCallback[] result = router.getRefundAgentTools();

        assertThat(result).hasSize(2);
        assertThat(result[0]).isInstanceOf(ResilientToolCallback.class);
        assertThat(result[1]).isInstanceOf(ResilientToolCallback.class);
    }

    @Test
    @DisplayName("filters tools correctly for knowledge agent")
    void getKnowledgeAgentTools_filtersCorrectly() {
        var provider = mock(ToolCallbackProvider.class);
        var searchProductsCallback = mockCallback("searchProducts");
        var getProductCallback = mockCallback("getProduct");
        var listCategoriesCallback = mockCallback("listCategories");
        var orderCallback = mockCallback("getOrder");
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{
                searchProductsCallback, getProductCallback, listCategoriesCallback, orderCallback
        });

        router = new McpToolRouter(List.of(provider), resilienceProps);
        ToolCallback[] result = router.getKnowledgeAgentTools();

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(cb -> cb instanceof ResilientToolCallback);
    }

    @Test
    @DisplayName("hasTools returns true when tools available")
    void hasTools_withTools_returnsTrue() {
        var provider = mock(ToolCallbackProvider.class);
        var callback = mockCallback("getOrder");
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{callback});

        router = new McpToolRouter(List.of(provider), resilienceProps);

        assertThat(router.hasTools()).isTrue();
    }

    @Test
    @DisplayName("hasTools returns false when no tools available")
    void hasTools_noTools_returnsFalse() {
        router = new McpToolRouter(null, resilienceProps);

        assertThat(router.hasTools()).isFalse();
    }

    @Test
    @DisplayName("wraps callbacks for all agent tool methods")
    void allAgentMethods_returnResilientCallbacks() {
        var provider = mock(ToolCallbackProvider.class);
        var orderCallback = mockCallback("getOrder");
        var refundCallback = mockCallback("checkRefundEligibility");
        var productCallback = mockCallback("searchProducts");
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{
                orderCallback, refundCallback, productCallback
        });

        router = new McpToolRouter(List.of(provider), resilienceProps);

        assertThat(router.getOrderAgentTools())
                .hasSize(1)
                .allMatch(cb -> cb instanceof ResilientToolCallback);
        assertThat(router.getRefundAgentTools())
                .hasSize(1)
                .allMatch(cb -> cb instanceof ResilientToolCallback);
        assertThat(router.getKnowledgeAgentTools())
                .hasSize(1)
                .allMatch(cb -> cb instanceof ResilientToolCallback);
    }

    // --- Helper ---

    private ToolCallback mockCallback(String name) {
        var callback = mock(ToolCallback.class);
        var toolDef = mock(ToolDefinition.class);
        when(toolDef.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(toolDef);
        return callback;
    }
}
