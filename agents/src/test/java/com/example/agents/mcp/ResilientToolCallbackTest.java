package com.example.agents.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResilientToolCallbackTest {

    private ToolCallback delegate;
    private McpResilienceProperties props;
    private ResilientToolCallback subject;

    @BeforeEach
    void setUp() {
        delegate = mock(ToolCallback.class);

        props = new McpResilienceProperties();
        props.setRetryMaxAttempts(2);
        props.setRetryInitialBackoffMs(10);          // fast in tests
        props.setCircuitBreakerFailureThreshold(3);
        props.setCircuitBreakerResetTimeoutMs(100);  // short for tests
        props.setMaxFailuresBeforeEscalation(3);

        ToolDefinition def = ToolDefinition.builder()
                .name("testTool")
                .description("A test tool")
                .inputSchema("{}")
                .build();
        when(delegate.getToolDefinition()).thenReturn(def);
        when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());

        subject = new ResilientToolCallback(delegate, props);
    }

    // 1. Delegates to underlying callback on success
    @Test
    @DisplayName("delegates to underlying callback on success")
    void call_success_delegatesToUnderlying() {
        when(delegate.call(anyString())).thenReturn("order details");

        String result = subject.call("{\"orderId\":\"123\"}");

        assertThat(result).isEqualTo("order details");
        verify(delegate, times(1)).call(anyString());
    }

    // 2. Retries on failure then succeeds
    @Test
    @DisplayName("retries on failure then succeeds on subsequent attempt")
    void call_failOnceThenSucceed_retriesAndReturnsSuccess() {
        when(delegate.call(anyString()))
                .thenThrow(new RuntimeException("transient error"))
                .thenReturn("success on retry");

        String result = subject.call("{}");

        assertThat(result).isEqualTo("success on retry");
        verify(delegate, times(2)).call(anyString());
    }

    // 3. Returns MCP_UNAVAILABLE after retries exhausted
    @Test
    @DisplayName("returns MCP_UNAVAILABLE string after all retries exhausted")
    void call_allRetriesFail_returnsMcpUnavailable() {
        when(delegate.call(anyString())).thenThrow(new RuntimeException("always fails"));

        String result = subject.call("{}");

        assertThat(result).startsWith("MCP_UNAVAILABLE:");
        // retryMaxAttempts=2 means 1 initial + 2 retries = 3 total
        verify(delegate, times(3)).call(anyString());
    }

    // 4. Circuit breaker opens after threshold failures
    @Test
    @DisplayName("circuit breaker opens after threshold consecutive call-sequence failures")
    void call_circuitBreakerOpensAfterThreshold() {
        when(delegate.call(anyString())).thenThrow(new RuntimeException("always fails"));

        // exhaust retries 3 times to trip the circuit breaker (threshold=3)
        subject.call("{}");
        subject.call("{}");
        subject.call("{}");

        // 4th call should be blocked immediately — delegate.call() must NOT be invoked again
        // retryMaxAttempts=2 → 3 attempts per sequence; 3 sequences = 9 delegate.call() invocations
        String result = subject.call("{}");

        assertThat(result).startsWith("MCP_UNAVAILABLE:");
        // Still exactly 9 delegate.call() invocations — circuit blocked the 4th sequence
        verify(delegate, times(9)).call(anyString());
    }

    // 5. Circuit breaker resets after timeout
    @Test
    @DisplayName("circuit breaker resets to half-open after reset timeout")
    void call_circuitBreakerResetsAfterTimeout() throws InterruptedException {
        when(delegate.call(anyString()))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenThrow(new RuntimeException("always fails"))
                .thenReturn("recovered");

        // trip circuit breaker
        subject.call("{}");
        subject.call("{}");
        subject.call("{}");

        // wait for reset timeout
        Thread.sleep(150);

        // should attempt again (half-open probe) and return the recovered value
        String result = subject.call("{}");

        assertThat(result).isEqualTo("recovered");
        verify(delegate, atLeast(4)).call(anyString());
    }

    // 6. Preserves tool definition from delegate
    @Test
    @DisplayName("getToolDefinition delegates to wrapped callback")
    void getToolDefinition_delegatesToUnderlying() {
        assertThat(subject.getToolDefinition().name()).isEqualTo("testTool");
        verify(delegate).getToolDefinition();
    }

    // 7. Tracks consecutive failure count
    @Test
    @DisplayName("tracks consecutive failures across call sequences")
    void getConsecutiveFailures_tracksCount() {
        when(delegate.call(anyString())).thenThrow(new RuntimeException("fail"));

        subject.call("{}");
        subject.call("{}");

        assertThat(subject.getConsecutiveFailures()).isEqualTo(2);
    }

    // 8. Resets failure count on success
    @Test
    @DisplayName("resets consecutive failure count after a successful call")
    void getConsecutiveFailures_resetsOnSuccess() {
        when(delegate.call(anyString()))
                .thenThrow(new RuntimeException("fail"))
                .thenThrow(new RuntimeException("fail"))
                .thenReturn("ok");

        subject.call("{}"); // fails (2 attempts exhaust retries... wait, retryMaxAttempts=2 so 3 total)
        // All 3 attempts fail → consecutive++ → 1
        // Then a successful call:
        when(delegate.call(anyString())).thenReturn("ok");
        subject.call("{}");

        assertThat(subject.getConsecutiveFailures()).isEqualTo(0);
    }

    // resetConsecutiveFailures manual reset
    @Test
    @DisplayName("resetConsecutiveFailures manually resets the failure count")
    void resetConsecutiveFailures_resetsManually() {
        when(delegate.call(anyString())).thenThrow(new RuntimeException("fail"));

        subject.call("{}");
        assertThat(subject.getConsecutiveFailures()).isEqualTo(1);

        subject.resetConsecutiveFailures();
        assertThat(subject.getConsecutiveFailures()).isEqualTo(0);
    }

    // call(String, ToolContext) delegates to call(String)
    @Test
    @DisplayName("call with ToolContext delegates to call(String)")
    void callWithToolContext_delegatesToCallString() {
        when(delegate.call(anyString())).thenReturn("result");

        String result = subject.call("{}", null);

        assertThat(result).isEqualTo("result");
        verify(delegate, times(1)).call(anyString());
    }
}
