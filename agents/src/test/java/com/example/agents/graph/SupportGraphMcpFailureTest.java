package com.example.agents.graph;

import com.example.agents.mcp.McpResilienceProperties;
import com.example.agents.mcp.ResilientToolCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for MCP failure tracking and escalation threshold logic.
 *
 * <p>These tests verify that ResilientToolCallback failure counters integrate correctly
 * with the SupportGraph's escalation decision logic. Specifically, they test:
 * - Failure tracking across multiple call sequences
 * - Escalation triggers when consecutive failures exceed the threshold
 * - Counter reset on successful calls
 */
class SupportGraphMcpFailureTest {

    private ToolCallback delegate;
    private McpResilienceProperties props;
    private ResilientToolCallback subject;

    @BeforeEach
    void setUp() {
        delegate = mock(ToolCallback.class);

        props = new McpResilienceProperties();
        props.setRetryMaxAttempts(1);
        props.setRetryInitialBackoffMs(10);
        props.setCircuitBreakerFailureThreshold(5);
        props.setCircuitBreakerResetTimeoutMs(100);
        props.setMaxFailuresBeforeEscalation(2);

        ToolDefinition def = ToolDefinition.builder()
                .name("getOrderDetails")
                .description("Fetch order details from MCP service")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}}}")
                .build();
        when(delegate.getToolDefinition()).thenReturn(def);
        when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());

        subject = new ResilientToolCallback(delegate, props);
    }

    /**
     * Test 1: ResilientToolCallback returns MCP_UNAVAILABLE and tracks failures correctly.
     *
     * <p>Setup:
     * - Mock ToolCallback that always throws RuntimeException
     * - retryMaxAttempts=1, maxFailuresBeforeEscalation=2
     * - Call resilient.call() twice
     *
     * <p>Assertions:
     * - Both calls return MCP_UNAVAILABLE
     * - consecutiveFailures == 2
     * - consecutiveFailures >= maxFailuresBeforeEscalation (triggers escalation)
     */
    @Test
    @DisplayName("Test 1: ResilientToolCallback returns MCP_UNAVAILABLE and tracks failures correctly")
    void mcpUnavailableTracksFailures() {
        // Setup: Mock always throws
        when(delegate.call(anyString()))
                .thenThrow(new RuntimeException("Order system unavailable"));

        // Call 1: Both attempts exhaust retries
        // retryMaxAttempts=1 → 1 initial + 1 retry = 2 total attempts
        String result1 = subject.call("{\"orderId\":\"123\"}");

        // Assert result is unavailable
        assertThat(result1).startsWith("MCP_UNAVAILABLE:");

        // Assert consecutive failures incremented
        assertThat(subject.getConsecutiveFailures()).isEqualTo(1);

        // Call 2: Exhausts retries again
        String result2 = subject.call("{\"orderId\":\"456\"}");

        // Assert result is unavailable
        assertThat(result2).startsWith("MCP_UNAVAILABLE:");

        // Assert consecutive failures == 2 (meets escalation threshold)
        assertThat(subject.getConsecutiveFailures()).isEqualTo(2);

        // Assert escalation condition: failures >= maxFailuresBeforeEscalation
        assertThat(subject.getConsecutiveFailures())
                .isGreaterThanOrEqualTo(props.getMaxFailuresBeforeEscalation());

        // Verify delegate was called 4 times (2 attempts × 2 calls)
        verify(delegate, times(4)).call(anyString());
    }

    /**
     * Test 2: Success after failures resets counter.
     *
     * <p>Setup:
     * - Mock: first call throws on all attempts, second call succeeds immediately
     * - retryMaxAttempts=1 (2 total attempts per call)
     *
     * <p>Assertions:
     * - First call exhausts retries → consecutiveFailures == 1
     * - Second call succeeds on first attempt → consecutiveFailures == 0
     */
    @Test
    @DisplayName("Test 2: Success after failures resets counter")
    void successAfterFailuresResetsCounter() {
        // Setup: First call always fails, second call succeeds
        when(delegate.call(anyString()))
                .thenThrow(new RuntimeException("Transient error"))
                .thenThrow(new RuntimeException("Transient error"))
                .thenReturn("Order Details: {\"id\":\"123\",\"status\":\"shipped\"}");

        // Call 1: Exhausts retries (2 attempts)
        String result1 = subject.call("{\"orderId\":\"123\"}");

        assertThat(result1).startsWith("MCP_UNAVAILABLE:");
        assertThat(subject.getConsecutiveFailures()).isEqualTo(1);

        // Call 2: Succeeds on first attempt
        String result2 = subject.call("{\"orderId\":\"456\"}");

        assertThat(result2).isEqualTo("Order Details: {\"id\":\"123\",\"status\":\"shipped\"}");
        // Assert counter was reset to 0 after successful call
        assertThat(subject.getConsecutiveFailures()).isEqualTo(0);

        // Verify delegate was called 3 times: 2 failed + 1 successful
        verify(delegate, times(3)).call(anyString());
    }

    /**
     * Test 3: Escalation threshold integration with multiple agents.
     *
     * <p>Simulates a scenario where multiple tool failures in the same agent
     * accumulate to trigger escalation.
     *
     * <p>Setup:
     * - maxFailuresBeforeEscalation = 2
     * - Create two resilient tool callbacks (simulating two MCP tools)
     *
     * <p>Assertions:
     * - Each tool tracks its own failure counter independently
     * - Total failures across both tools determine escalation
     */
    @Test
    @DisplayName("Test 3: Multiple tools accumulate failures toward escalation threshold")
    void multipleToolsAccumulateFailures() {
        // Setup: Two separate tools, each tracking independent failure counts
        ToolCallback delegate1 = mock(ToolCallback.class);
        ToolDefinition def1 = ToolDefinition.builder()
                .name("getOrderStatus")
                .description("Get order status")
                .inputSchema("{}")
                .build();
        when(delegate1.getToolDefinition()).thenReturn(def1);
        when(delegate1.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
        when(delegate1.call(anyString())).thenThrow(new RuntimeException("Service down"));

        ToolCallback delegate2 = mock(ToolCallback.class);
        ToolDefinition def2 = ToolDefinition.builder()
                .name("trackShipment")
                .description("Track shipment")
                .inputSchema("{}")
                .build();
        when(delegate2.getToolDefinition()).thenReturn(def2);
        when(delegate2.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
        when(delegate2.call(anyString())).thenThrow(new RuntimeException("Service down"));

        ResilientToolCallback tool1 = new ResilientToolCallback(delegate1, props);
        ResilientToolCallback tool2 = new ResilientToolCallback(delegate2, props);

        // Both tools fail once
        tool1.call("{}");
        tool2.call("{}");

        // Assert each tool has independent failure count
        assertThat(tool1.getConsecutiveFailures()).isEqualTo(1);
        assertThat(tool2.getConsecutiveFailures()).isEqualTo(1);

        // Total failures across both tools: 1 + 1 = 2 (meets escalation threshold)
        int totalFailures = tool1.getConsecutiveFailures() + tool2.getConsecutiveFailures();
        assertThat(totalFailures).isGreaterThanOrEqualTo(props.getMaxFailuresBeforeEscalation());
    }

    /**
     * Test 4: Manual counter reset (simulating orchestrator reset between turns).
     *
     * <p>The orchestrator may explicitly reset failure counters between conversation turns.
     *
     * <p>Setup:
     * - Tool fails
     * - consecutiveFailures == 1
     * - Call resetConsecutiveFailures()
     *
     * <p>Assertions:
     * - After reset, consecutiveFailures == 0
     * - Counter starts fresh on next call
     */
    @Test
    @DisplayName("Test 4: Manual counter reset for between-turn orchestration")
    void manualCounterReset() {
        when(delegate.call(anyString())).thenThrow(new RuntimeException("Fail"));

        // Tool fails
        subject.call("{}");
        assertThat(subject.getConsecutiveFailures()).isEqualTo(1);

        // Orchestrator explicitly resets (e.g., escalating, then resuming)
        subject.resetConsecutiveFailures();
        assertThat(subject.getConsecutiveFailures()).isEqualTo(0);

        // Counter should allow tracking fresh failures
        subject.call("{}");
        assertThat(subject.getConsecutiveFailures()).isEqualTo(1);
    }
}
