# MCP-First Agent Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate local/MCP tool duplication, add MCP resilience (retry + circuit breaker + auto-escalation), make intent classification context-aware, and fix multi-turn context injection.

**Architecture:** All order/refund data flows exclusively through the MCP server (ecommerce-mcp SSE at `${ECOMMERCE_MCP_URL}`). Local data providers and tool wrappers are deleted. A `ResilientToolCallback` decorator adds retry + circuit breaker around each MCP tool callback. The `IntentClassifier` receives conversation history for accurate follow-up classification. The `SupportGraph` tracks MCP failure count in state and auto-escalates after N failures.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring AI 1.0.0, LangGraph4j 1.8.19

## Global Constraints

- Java 21, Spring AI 1.0.0 — `ToolCallback` interface: `getToolDefinition()`, `getToolMetadata()`, `call(String)`, `call(String, ToolContext)`
- MCP client: `spring-ai-starter-mcp-client-webflux`
- LangGraph4j state: extends `MessagesState<Message>`, uses `Map<String, Object>` returns from nodes
- Existing tests: none in agents module — all tests are new
- All config under `app.mcp.*` prefix in `application.yml`

---

### Task 1: MCP Resilience — Config Properties + ResilientToolCallback

**Files:**
- Create: `agents/src/main/java/com/example/agents/mcp/McpResilienceProperties.java`
- Create: `agents/src/main/java/com/example/agents/mcp/ResilientToolCallback.java`
- Test: `agents/src/test/java/com/example/agents/mcp/ResilientToolCallbackTest.java`

**Interfaces:**
- Consumes: `org.springframework.ai.tool.ToolCallback` (Spring AI), `org.springframework.ai.tool.definition.ToolDefinition`
- Produces:
  - `McpResilienceProperties` — `retryMaxAttempts()`, `retryInitialBackoffMs()`, `circuitBreakerFailureThreshold()`, `circuitBreakerResetTimeoutMs()`, `maxFailuresBeforeEscalation()`
  - `ResilientToolCallback implements ToolCallback` — wraps a delegate `ToolCallback` with retry + circuit breaker. Constructor: `ResilientToolCallback(ToolCallback delegate, McpResilienceProperties props)`. Returns `"MCP_UNAVAILABLE: ..."` string on exhausted retries instead of throwing.

- [ ] **Step 1: Write the test for ResilientToolCallback**

Create `agents/src/test/java/com/example/agents/mcp/ResilientToolCallbackTest.java`:

```java
package com.example.agents.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResilientToolCallbackTest {

    private ToolCallback delegate;
    private McpResilienceProperties props;

    @BeforeEach
    void setUp() {
        delegate = mock(ToolCallback.class);
        var toolDef = mock(ToolDefinition.class);
        when(toolDef.name()).thenReturn("getOrder");
        when(delegate.getToolDefinition()).thenReturn(toolDef);

        props = new McpResilienceProperties();
        props.setRetryMaxAttempts(2);
        props.setRetryInitialBackoffMs(10); // fast for tests
        props.setCircuitBreakerFailureThreshold(3);
        props.setCircuitBreakerResetTimeoutMs(100);
        props.setMaxFailuresBeforeEscalation(3);
    }

    @Test
    @DisplayName("delegates to underlying callback on success")
    void call_success_delegates() {
        when(delegate.call("{\"orderId\":\"123\"}")).thenReturn("Order Details: ...");

        var resilient = new ResilientToolCallback(delegate, props);
        String result = resilient.call("{\"orderId\":\"123\"}");

        assertThat(result).isEqualTo("Order Details: ...");
        verify(delegate, times(1)).call("{\"orderId\":\"123\"}");
    }

    @Test
    @DisplayName("retries on failure then succeeds")
    void call_retryThenSuccess() {
        when(delegate.call("{\"orderId\":\"123\"}"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenReturn("Order Details: ...");

        var resilient = new ResilientToolCallback(delegate, props);
        String result = resilient.call("{\"orderId\":\"123\"}");

        assertThat(result).isEqualTo("Order Details: ...");
        verify(delegate, times(2)).call("{\"orderId\":\"123\"}");
    }

    @Test
    @DisplayName("returns MCP_UNAVAILABLE after retries exhausted")
    void call_allRetriesFail_returnsMcpUnavailable() {
        when(delegate.call("{\"orderId\":\"123\"}"))
            .thenThrow(new RuntimeException("Connection refused"));

        var resilient = new ResilientToolCallback(delegate, props);
        String result = resilient.call("{\"orderId\":\"123\"}");

        assertThat(result).startsWith("MCP_UNAVAILABLE:");
        // 1 initial + 2 retries = 3 total attempts
        verify(delegate, times(3)).call("{\"orderId\":\"123\"}");
    }

    @Test
    @DisplayName("circuit breaker opens after threshold failures")
    void call_circuitBreakerOpens() {
        when(delegate.call(anyString()))
            .thenThrow(new RuntimeException("Connection refused"));

        var resilient = new ResilientToolCallback(delegate, props);

        // Exhaust circuit breaker: threshold=3, each call does 3 attempts (1+2 retries)
        resilient.call("{}");
        resilient.call("{}");
        resilient.call("{}");

        // Circuit is now open — should return immediately without calling delegate
        int callsBefore = mockingDetails(delegate).getInvocations().size();
        String result = resilient.call("{}");

        assertThat(result).startsWith("MCP_UNAVAILABLE:");
        assertThat(mockingDetails(delegate).getInvocations().size()).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("circuit breaker resets after timeout")
    void call_circuitBreakerResets() throws InterruptedException {
        when(delegate.call(anyString()))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenThrow(new RuntimeException("Connection refused"))
            .thenReturn("Order Details: recovered");

        var resilient = new ResilientToolCallback(delegate, props);

        // Trip the circuit breaker (3 failed call sequences)
        resilient.call("{}");
        resilient.call("{}");
        resilient.call("{}");

        // Wait for reset timeout
        Thread.sleep(150);

        // Half-open: should try again
        String result = resilient.call("{}");
        assertThat(result).isEqualTo("Order Details: recovered");
    }

    @Test
    @DisplayName("preserves tool definition from delegate")
    void getToolDefinition_delegatesCorrectly() {
        var resilient = new ResilientToolCallback(delegate, props);
        assertThat(resilient.getToolDefinition().name()).isEqualTo("getOrder");
    }

    @Test
    @DisplayName("tracks consecutive failure count")
    void getConsecutiveFailures_tracksCorrectly() {
        when(delegate.call(anyString()))
            .thenThrow(new RuntimeException("fail"));

        var resilient = new ResilientToolCallback(delegate, props);
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(0);

        resilient.call("{}");
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(1);

        resilient.call("{}");
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(2);
    }

    @Test
    @DisplayName("resets failure count on success")
    void getConsecutiveFailures_resetsOnSuccess() {
        when(delegate.call(anyString()))
            .thenThrow(new RuntimeException("fail"))
            .thenThrow(new RuntimeException("fail"))
            .thenThrow(new RuntimeException("fail"))
            .thenReturn("OK");

        var resilient = new ResilientToolCallback(delegate, props);
        resilient.call("{}"); // fails (retries exhausted)
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(1);

        // Next call succeeds after 1 retry
        when(delegate.call(anyString())).thenReturn("OK");
        resilient.call("{}");
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :agents:test --tests "com.example.agents.mcp.ResilientToolCallbackTest" --info`
Expected: Compilation error — `ResilientToolCallback` and `McpResilienceProperties` don't exist yet.

- [ ] **Step 3: Create McpResilienceProperties**

Create `agents/src/main/java/com/example/agents/mcp/McpResilienceProperties.java`:

```java
package com.example.agents.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mcp")
public class McpResilienceProperties {

    private int retryMaxAttempts = 2;
    private long retryInitialBackoffMs = 500;
    private int circuitBreakerFailureThreshold = 3;
    private long circuitBreakerResetTimeoutMs = 30000;
    private int maxFailuresBeforeEscalation = 3;

    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }

    public long getRetryInitialBackoffMs() { return retryInitialBackoffMs; }
    public void setRetryInitialBackoffMs(long retryInitialBackoffMs) { this.retryInitialBackoffMs = retryInitialBackoffMs; }

    public int getCircuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) { this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold; }

    public long getCircuitBreakerResetTimeoutMs() { return circuitBreakerResetTimeoutMs; }
    public void setCircuitBreakerResetTimeoutMs(long circuitBreakerResetTimeoutMs) { this.circuitBreakerResetTimeoutMs = circuitBreakerResetTimeoutMs; }

    public int getMaxFailuresBeforeEscalation() { return maxFailuresBeforeEscalation; }
    public void setMaxFailuresBeforeEscalation(int maxFailuresBeforeEscalation) { this.maxFailuresBeforeEscalation = maxFailuresBeforeEscalation; }
}
```

- [ ] **Step 4: Create ResilientToolCallback**

Create `agents/src/main/java/com/example/agents/mcp/ResilientToolCallback.java`:

```java
package com.example.agents.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ResilientToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ResilientToolCallback.class);

    private final ToolCallback delegate;
    private final McpResilienceProperties props;

    // Circuit breaker state
    private final AtomicInteger cbFailureCount = new AtomicInteger(0);
    private final AtomicLong cbOpenedAt = new AtomicLong(0);
    private volatile boolean cbOpen = false;

    // Consecutive call-level failure tracking (for escalation logic)
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public ResilientToolCallback(ToolCallback delegate, McpResilienceProperties props) {
        this.delegate = delegate;
        this.props = props;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String toolName = delegate.getToolDefinition().name();

        // Circuit breaker check
        if (cbOpen) {
            long elapsed = System.currentTimeMillis() - cbOpenedAt.get();
            if (elapsed < props.getCircuitBreakerResetTimeoutMs()) {
                log.warn("Circuit breaker OPEN for MCP tool '{}' — skipping call", toolName);
                consecutiveFailures.incrementAndGet();
                return "MCP_UNAVAILABLE: The order system is temporarily unavailable. Please try again in a few minutes.";
            }
            // Half-open: allow one attempt
            log.info("Circuit breaker HALF-OPEN for MCP tool '{}' — allowing probe", toolName);
            cbOpen = false;
            cbFailureCount.set(0);
        }

        // Retry loop
        int maxAttempts = 1 + props.getRetryMaxAttempts();
        long backoff = props.getRetryInitialBackoffMs();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String result = delegate.call(toolInput);
                // Success — reset counters
                cbFailureCount.set(0);
                consecutiveFailures.set(0);
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("MCP tool '{}' attempt {}/{} failed: {}", toolName, attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoff *= 2;
                }
            }
        }

        // All retries failed
        consecutiveFailures.incrementAndGet();
        int failures = cbFailureCount.incrementAndGet();
        if (failures >= props.getCircuitBreakerFailureThreshold()) {
            cbOpen = true;
            cbOpenedAt.set(System.currentTimeMillis());
            log.error("Circuit breaker OPENED for MCP tool '{}' after {} consecutive failures", toolName, failures);
        }

        log.error("MCP tool '{}' failed after {} attempts: {}", toolName, maxAttempts, lastException.getMessage());
        return "MCP_UNAVAILABLE: The order system is temporarily unavailable. Please try again in a few minutes.";
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // Delegate to the single-arg version which has resilience logic
        return call(toolInput);
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void resetConsecutiveFailures() {
        consecutiveFailures.set(0);
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :agents:test --tests "com.example.agents.mcp.ResilientToolCallbackTest" --info`
Expected: All 8 tests PASS.

- [ ] **Step 6: Add MCP config to application.yml**

Add the following block to `api/src/main/resources/application.yml` under the existing `app:` section (after the `allowed-origins` line, around line 78):

```yaml
  mcp:
    retry:
      max-attempts: ${MCP_RETRY_MAX_ATTEMPTS:2}
      initial-backoff-ms: ${MCP_RETRY_BACKOFF_MS:500}
    circuit-breaker:
      failure-threshold: ${MCP_CB_FAILURE_THRESHOLD:3}
      reset-timeout-ms: ${MCP_CB_RESET_TIMEOUT_MS:30000}
    max-failures-before-escalation: ${MCP_MAX_FAILURES_ESCALATION:3}
```

Note: The `McpResilienceProperties` uses a flat property mapping. The YAML nesting `app.mcp.retry.max-attempts` maps to `app.mcp.retry-max-attempts` which Spring Boot binds to `setRetryMaxAttempts`. Alternatively, use the flat form:

```yaml
  mcp:
    retry-max-attempts: ${MCP_RETRY_MAX_ATTEMPTS:2}
    retry-initial-backoff-ms: ${MCP_RETRY_BACKOFF_MS:500}
    circuit-breaker-failure-threshold: ${MCP_CB_FAILURE_THRESHOLD:3}
    circuit-breaker-reset-timeout-ms: ${MCP_CB_RESET_TIMEOUT_MS:30000}
    max-failures-before-escalation: ${MCP_MAX_FAILURES_ESCALATION:3}
```

- [ ] **Step 7: Commit**

```bash
git add agents/src/main/java/com/example/agents/mcp/McpResilienceProperties.java \
      agents/src/main/java/com/example/agents/mcp/ResilientToolCallback.java \
      agents/src/test/java/com/example/agents/mcp/ResilientToolCallbackTest.java \
      api/src/main/resources/application.yml
git commit -m "feat: add MCP resilience layer with retry, circuit breaker, and failure tracking"
```

---

### Task 2: Update McpToolRouter to Wrap Callbacks with Resilience

**Files:**
- Modify: `agents/src/main/java/com/example/agents/mcp/McpToolRouter.java`
- Test: `agents/src/test/java/com/example/agents/mcp/McpToolRouterTest.java`

**Interfaces:**
- Consumes: `ResilientToolCallback` (Task 1), `McpResilienceProperties` (Task 1)
- Produces: `McpToolRouter.getOrderAgentTools()`, `getRefundAgentTools()`, `getKnowledgeAgentTools()` — now return `ResilientToolCallback[]` instead of raw `ToolCallback[]`. New method: `McpToolRouter.hasRecentFailures()` returning `true` if any wrapped callback has `consecutiveFailures > 0`.

- [ ] **Step 1: Write the test for McpToolRouter**

Create `agents/src/test/java/com/example/agents/mcp/McpToolRouterTest.java`:

```java
package com.example.agents.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class McpToolRouterTest {

    private ToolCallback mockCallback(String name) {
        var cb = mock(ToolCallback.class);
        var def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }

    @Test
    @DisplayName("wraps MCP callbacks with ResilientToolCallback")
    void getOrderAgentTools_wrapsWithResilience() {
        var provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{
            mockCallback("getOrder"),
            mockCallback("listCustomerOrders"),
            mockCallback("getCustomer"),
            mockCallback("searchCustomers"),
            mockCallback("searchProducts")
        });

        var props = new McpResilienceProperties();
        var router = new McpToolRouter(List.of(provider), props);

        ToolCallback[] orderTools = router.getOrderAgentTools();
        assertThat(orderTools).hasSize(4);
        for (ToolCallback cb : orderTools) {
            assertThat(cb).isInstanceOf(ResilientToolCallback.class);
        }
    }

    @Test
    @DisplayName("returns empty array when no providers available")
    void getOrderAgentTools_noProviders_returnsEmpty() {
        var props = new McpResilienceProperties();
        var router = new McpToolRouter(null, props);

        assertThat(router.getOrderAgentTools()).isEmpty();
        assertThat(router.hasTools()).isFalse();
    }

    @Test
    @DisplayName("filters tools correctly per agent domain")
    void getRefundAgentTools_filtersCorrectly() {
        var provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{
            mockCallback("checkRefundEligibility"),
            mockCallback("getRefundStatus"),
            mockCallback("getOrder")
        });

        var props = new McpResilienceProperties();
        var router = new McpToolRouter(List.of(provider), props);

        assertThat(router.getRefundAgentTools()).hasSize(2);
        assertThat(router.getOrderAgentTools()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :agents:test --tests "com.example.agents.mcp.McpToolRouterTest" --info`
Expected: Compilation error — `McpToolRouter` constructor doesn't accept `McpResilienceProperties` yet.

- [ ] **Step 3: Update McpToolRouter**

Modify `agents/src/main/java/com/example/agents/mcp/McpToolRouter.java` to:

```java
package com.example.agents.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class McpToolRouter {

    private static final Logger log = LoggerFactory.getLogger(McpToolRouter.class);

    private static final Set<String> ORDER_AGENT_TOOLS = Set.of(
            "getOrder", "listCustomerOrders", "getCustomer", "searchCustomers"
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :agents:test --tests "com.example.agents.mcp.McpToolRouterTest" --info`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add agents/src/main/java/com/example/agents/mcp/McpToolRouter.java \
      agents/src/test/java/com/example/agents/mcp/McpToolRouterTest.java
git commit -m "feat: wrap MCP tool callbacks with resilience in McpToolRouter"
```

---

### Task 3: Context-Aware Intent Classification

**Files:**
- Modify: `agents/src/main/java/com/example/agents/orchestrator/IntentClassifier.java`
- Test: `agents/src/test/java/com/example/agents/orchestrator/IntentClassifierTest.java`

**Interfaces:**
- Consumes: `AgentType` enum, `IntentClassification` record, `org.springframework.ai.chat.messages.Message`
- Produces: `IntentClassifier.classify(String userMessage, List<Message> conversationHistory)` — the old single-arg method still works (delegates with empty list for backward compat)

- [ ] **Step 1: Write the test**

Create `agents/src/test/java/com/example/agents/orchestrator/IntentClassifierTest.java`:

```java
package com.example.agents.orchestrator;

import com.example.agents.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntentClassifierTest {

    private ChatModel chatModel;
    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        // Default: LLM returns ORDER for ambiguous messages
        var generation = new Generation(new AssistantMessage("ORDER"));
        var chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(chatResponse);

        classifier = new IntentClassifier(chatModel);
    }

    @Test
    @DisplayName("keyword match: 'track my order' routes to ORDER with high confidence")
    void classify_trackOrder_keywordMatch() {
        var result = classifier.classify("track my order", List.of());
        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
        assertThat(result.isHighConfidence()).isTrue();
    }

    @Test
    @DisplayName("keyword match: 'refund' routes to REFUND with high confidence")
    void classify_refund_keywordMatch() {
        var result = classifier.classify("I want a refund", List.of());
        assertThat(result.targetAgent()).isEqualTo(AgentType.REFUND);
        assertThat(result.isHighConfidence()).isTrue();
    }

    @Test
    @DisplayName("keyword match: 'return' routes to REFUND with high confidence")
    void classify_return_keywordMatch() {
        var result = classifier.classify("I want to return this", List.of());
        assertThat(result.targetAgent()).isEqualTo(AgentType.REFUND);
        assertThat(result.isHighConfidence()).isTrue();
    }

    @Test
    @DisplayName("bare order ID with no history falls to LLM classification")
    void classify_bareOrderId_noHistory_usesLlm() {
        var result = classifier.classify("d6461ecc120609d0cef78fea638e47fe", List.of());
        // LLM mock returns ORDER
        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
    }

    @Test
    @DisplayName("bare order ID with order conversation history uses LLM with context")
    void classify_bareOrderId_withOrderHistory_usesLlmWithContext() {
        List<Message> history = List.of(
            new UserMessage("Show me my recent orders"),
            new AssistantMessage("Here is your recent order: Order #ABC123 (delivered)")
        );

        var result = classifier.classify("d6461ecc120609d0cef78fea638e47fe", history);
        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
        // Verify LLM was called (no keyword match for bare ID)
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    @DisplayName("single-arg classify delegates to two-arg with empty history")
    void classify_singleArg_delegatesToTwoArg() {
        var result = classifier.classify("where is my order");
        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
        assertThat(result.isHighConfidence()).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :agents:test --tests "com.example.agents.orchestrator.IntentClassifierTest" --info`
Expected: Compilation error — `classify` doesn't accept `List<Message>` yet.

- [ ] **Step 3: Update IntentClassifier**

Replace `agents/src/main/java/com/example/agents/orchestrator/IntentClassifier.java` with:

```java
package com.example.agents.orchestrator;

import com.example.agents.AgentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IntentClassifier {

    private static final int MAX_HISTORY_MESSAGES = 6;

    private final ChatClient classifierClient;

    public IntentClassifier(ChatModel chatModel) {
        this.classifierClient = ChatClient.builder(chatModel)
            .defaultSystem("""
                You are an intent classifier for a customer support system.
                Given the conversation history and the latest customer message,
                classify the intent into exactly ONE category:

                ORDER - Order lookup, tracking, shipment status, delivery questions, order cancellation
                REFUND - Refund requests, return requests, money back, overcharges, refund status
                KNOWLEDGE - Policy questions, FAQ, product information, general inquiries
                ESCALATION - Explicit request for human agent, complaints, unresolved frustration

                Respond with ONLY the category name (ORDER, REFUND, KNOWLEDGE, or ESCALATION).
                Nothing else.
                """)
            .build();
    }

    public IntentClassification classify(String userMessage) {
        return classify(userMessage, List.of());
    }

    public IntentClassification classify(String userMessage, List<Message> conversationHistory) {
        IntentClassification keywordResult = keywordClassify(userMessage);
        if (keywordResult.isHighConfidence()) {
            return keywordResult;
        }

        try {
            String prompt = buildClassificationPrompt(userMessage, conversationHistory);
            String result = classifierClient.prompt()
                .user(prompt)
                .call()
                .content();

            AgentType agentType = parseAgentType(result.trim().toUpperCase());
            return new IntentClassification(agentType, 0.85, "LLM classification: " + result);
        } catch (Exception e) {
            return new IntentClassification(AgentType.KNOWLEDGE, 0.5,
                "Classification failed, defaulting to knowledge");
        }
    }

    private String buildClassificationPrompt(String userMessage, List<Message> history) {
        var sb = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            sb.append("CONVERSATION HISTORY:\n");
            int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            for (int i = start; i < history.size(); i++) {
                Message msg = history.get(i);
                String role = msg instanceof UserMessage ? "CUSTOMER" : "AGENT";
                sb.append(role).append(": ").append(msg.getText()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("LATEST MESSAGE:\n").append(userMessage);
        return sb.toString();
    }

    private IntentClassification keywordClassify(String message) {
        String lower = message.toLowerCase();

        if (containsAny(lower, "track", "where is my order", "shipping status",
                "delivery", "when will", "order status", "my order")) {
            return new IntentClassification(AgentType.ORDER, 0.9, "Keyword: order/tracking");
        }
        if (containsAny(lower, "cancel my order", "cancel order", "cancellation")) {
            return new IntentClassification(AgentType.ORDER, 0.9, "Keyword: cancellation");
        }
        if (containsAny(lower, "refund", "return", "money back", "reimburse",
                "credit back", "overcharged")) {
            return new IntentClassification(AgentType.REFUND, 0.9, "Keyword: refund/return");
        }
        if (containsAny(lower, "speak to human", "talk to agent", "real person",
                "manager", "supervisor", "complaint", "frustrated", "unacceptable")) {
            return new IntentClassification(AgentType.ESCALATION, 0.9, "Keyword: escalation");
        }
        if (containsAny(lower, "policy", "warranty", "terms", "how does",
                "what is your", "do you offer")) {
            return new IntentClassification(AgentType.KNOWLEDGE, 0.8, "Keyword: policy/FAQ");
        }

        return new IntentClassification(AgentType.KNOWLEDGE, 0.4, "No keyword match");
    }

    private AgentType parseAgentType(String text) {
        if (text.contains("ORDER")) return AgentType.ORDER;
        if (text.contains("REFUND")) return AgentType.REFUND;
        if (text.contains("ESCALATION")) return AgentType.ESCALATION;
        return AgentType.KNOWLEDGE;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :agents:test --tests "com.example.agents.orchestrator.IntentClassifierTest" --info`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add agents/src/main/java/com/example/agents/orchestrator/IntentClassifier.java \
      agents/src/test/java/com/example/agents/orchestrator/IntentClassifierTest.java
git commit -m "feat: context-aware intent classification using conversation history"
```

---

### Task 4: Delete Local Tool Duplication + Update SupportGraph

**Files:**
- Delete: `agents/src/main/java/com/example/agents/tools/OrderTools.java`
- Delete: `agents/src/main/java/com/example/agents/tools/RefundTools.java`
- Delete: `agents/src/main/java/com/example/agents/tools/OrderDataProvider.java`
- Delete: `agents/src/main/java/com/example/agents/tools/RefundDataProvider.java`
- Delete: `agents/src/main/java/com/example/agents/tools/olist/OlistOrderDataProvider.java`
- Delete: `agents/src/main/java/com/example/agents/tools/olist/OlistRefundDataProvider.java`
- Delete: `agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonOrderDataProvider.java`
- Delete: `agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonRefundDataProvider.java`
- Delete: `agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonClient.java`
- Delete: `agents/src/main/java/com/example/agents/subagent/OrderAgent.java`
- Delete: `agents/src/main/java/com/example/agents/subagent/RefundAgent.java`
- Modify: `agents/src/main/java/com/example/agents/graph/SupportGraphState.java` (add `MCP_FAILURE_COUNT`)
- Modify: `agents/src/main/java/com/example/agents/graph/SupportGraph.java` (remove local tools, add failure routing, fix context injection, pass history to classifier)

**Interfaces:**
- Consumes: `McpToolRouter` (Task 2), `IntentClassifier.classify(String, List<Message>)` (Task 3), `ResilientToolCallback.getConsecutiveFailures()` (Task 1), `McpResilienceProperties` (Task 1)
- Produces: Updated `SupportGraph.invoke()` — same signature, but internally uses MCP-only tools, tracks failures, auto-escalates

- [ ] **Step 1: Delete local tool files**

Delete these 9 files:

```bash
rm agents/src/main/java/com/example/agents/tools/OrderTools.java
rm agents/src/main/java/com/example/agents/tools/RefundTools.java
rm agents/src/main/java/com/example/agents/tools/OrderDataProvider.java
rm agents/src/main/java/com/example/agents/tools/RefundDataProvider.java
rm agents/src/main/java/com/example/agents/tools/olist/OlistOrderDataProvider.java
rm agents/src/main/java/com/example/agents/tools/olist/OlistRefundDataProvider.java
rm agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonOrderDataProvider.java
rm agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonRefundDataProvider.java
rm agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonClient.java
```

- [ ] **Step 2: Delete legacy subagent files that reference deleted tools**

```bash
rm agents/src/main/java/com/example/agents/subagent/OrderAgent.java
rm agents/src/main/java/com/example/agents/subagent/RefundAgent.java
```

These are already disabled (`@Component` commented out) and import deleted `OrderTools`/`RefundTools`.

- [ ] **Step 3: Update SupportGraphState — add MCP_FAILURE_COUNT**

Add to `agents/src/main/java/com/example/agents/graph/SupportGraphState.java` after line 26 (`ECOM_CUSTOMER_ID`):

```java
public static final String MCP_FAILURE_COUNT = "mcpFailureCount";
```

Add accessor method after the `handledBy()` method (after line 81):

```java
public int mcpFailureCount() {
    return this.<Integer>value(MCP_FAILURE_COUNT).orElse(0);
}
```

- [ ] **Step 4: Update SupportGraph — remove local tools, add resilience routing, fix context, pass history**

Replace `agents/src/main/java/com/example/agents/graph/SupportGraph.java` with:

```java
package com.example.agents.graph;

import com.example.agents.AgentContext;
import com.example.agents.AgentType;
import com.example.agents.guardrails.ConversationGuardrails;
import com.example.agents.orchestrator.IntentClassifier;
import com.example.agents.mcp.McpResilienceProperties;
import com.example.agents.mcp.McpToolRouter;
import com.example.agents.mcp.ResilientToolCallback;
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

    // Store MCP tool arrays to inspect failure counts after calls
    private final ToolCallback[] orderMcpTools;
    private final ToolCallback[] refundMcpTools;

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

        this.orderClient = ChatClient.builder(chatModel)
            .defaultSystem("""
                You are an Order Support Agent for an e-commerce platform.
                Help with order lookups, tracking, shipment status, delivery questions, and order cancellations.
                Be precise with order details. Always confirm before destructive actions.
                Use the conversation history to maintain context across turns.

                CRITICAL RULES:
                - NEVER fabricate or invent order details. Only report information returned by your tools.
                - If a tool returns ORDER_NOT_FOUND, tell the user that no order was found with that ID.
                  Ask them to double-check their order ID and try again.
                - If a tool returns MCP_UNAVAILABLE, tell the user the order system is temporarily
                  unavailable and ask them to try again in a few minutes.
                - If a tool returns NO_SHIPMENT, explain that the order hasn't shipped yet.
                - Do NOT guess or make up shipping carriers, tracking numbers, delivery dates, or order contents.
                """)
            .defaultTools(askUserQuestionTool)
            .defaultToolCallbacks(orderMcpTools)
            .build();

        this.refundClient = ChatClient.builder(chatModel)
            .defaultSystem("""
                You are a Refund Support Agent for an e-commerce platform.
                Help with refund eligibility, processing refund requests, checking refund status, and explaining return policies.
                Always check eligibility first. Confirm amounts before processing. Never process without explicit confirmation.
                Use the conversation history to maintain context across turns.

                CRITICAL RULES:
                - If a tool returns MCP_UNAVAILABLE, tell the user the refund system is temporarily
                  unavailable and ask them to try again in a few minutes.
                """)
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

        stateGraph.addEdge(START, GUARDRAILS);

        stateGraph.addConditionalEdges(GUARDRAILS,
            edge_async(this::afterGuardrails),
            Map.of(
                "pass", CACHE_CHECK,
                "reject", POST_PROCESS,
                "escalate", ESCALATION_AGENT
            )
        );

        stateGraph.addConditionalEdges(CACHE_CHECK,
            edge_async(this::afterCacheCheck),
            Map.of(
                "hit", POST_PROCESS,
                "miss", BUILD_CONTEXT
            )
        );

        stateGraph.addEdge(BUILD_CONTEXT, ROUTER);

        stateGraph.addConditionalEdges(ROUTER,
            edge_async(this::afterRouter),
            Map.of(
                "ORDER", ORDER_AGENT,
                "REFUND", REFUND_AGENT,
                "KNOWLEDGE", KNOWLEDGE_AGENT,
                "ESCALATION", ESCALATION_AGENT
            )
        );

        // MCP-dependent agents use conditional edges for failure-based escalation
        stateGraph.addConditionalEdges(ORDER_AGENT,
            edge_async(this::afterMcpAgent),
            Map.of("escalate", ESCALATION_AGENT, "continue", POST_PROCESS)
        );
        stateGraph.addConditionalEdges(REFUND_AGENT,
            edge_async(this::afterMcpAgent),
            Map.of("escalate", ESCALATION_AGENT, "continue", POST_PROCESS)
        );

        // Non-MCP agents go straight to post-process
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
        var intent = intentClassifier.classify(lastMsg, state.messages());
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
        var messages = state.messages();
        var prompt = client.prompt();

        if (messages != null && !messages.isEmpty()) {
            prompt.messages(messages);
        }

        // Inject ecommerce customer context dynamically into system prompt
        String ecomCustId = state.ecomCustomerId();
        if (ecomCustId != null && ("ORDER".equals(agentName) || "REFUND".equals(agentName))) {
            prompt.system("The current user's ecommerce customer ID is: " + ecomCustId
                + ". Use this ID when calling order, customer, or refund tools.");
        } else if (ecomCustId == null && ("ORDER".equals(agentName) || "REFUND".equals(agentName))) {
            prompt.system("This user has no linked ecommerce account. "
                + "If they ask about orders or refunds, inform them that their account is not linked "
                + "and suggest they contact support to link their account.");
        }

        String response = prompt.call().content();

        // Check MCP failure count from resilient callbacks
        int failureCount = countMcpFailures(agentName);

        var result = new HashMap<String, Object>();
        result.put(SupportGraphState.RESPONSE_TEXT, response);
        result.put(SupportGraphState.HANDLED_BY, agentName);
        result.put("messages", new AssistantMessage(response));
        if (failureCount > 0) {
            result.put(SupportGraphState.MCP_FAILURE_COUNT,
                state.mcpFailureCount() + failureCount);
        }
        return result;
    }

    private String afterMcpAgent(SupportGraphState state) {
        int failures = state.mcpFailureCount();
        if (failures >= mcpProps.getMaxFailuresBeforeEscalation()) {
            log.warn("MCP failure count {} >= threshold {} — auto-escalating",
                failures, mcpProps.getMaxFailuresBeforeEscalation());
            return "escalate";
        }
        return "continue";
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

        String lastUserMsg = getLastUserMessageText(state);
        String responseText = state.responseText();

        memoryManager.episodic().addMessage(conversationId, MessageRole.USER, lastUserMsg);
        memoryManager.episodic().addMessage(conversationId, MessageRole.ASSISTANT, responseText);

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
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :agents:compileJava --info`
Expected: BUILD SUCCESSFUL. No compilation errors.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: remove local order/refund tools, wire MCP-only with failure routing

Delete OrderTools, RefundTools, all DataProvider implementations,
DummyJsonClient, and legacy subagent files (OrderAgent, RefundAgent).
SupportGraph now uses MCP tools exclusively with auto-escalation
after N consecutive MCP failures. Intent classifier receives
conversation history. Context injection uses system prompt instead
of UserMessage hack."
```

---

### Task 5: Integration Test — MCP Failure Auto-Escalation

**Files:**
- Create: `agents/src/test/java/com/example/agents/graph/SupportGraphMcpFailureTest.java`

**Interfaces:**
- Consumes: `SupportGraph.invoke()`, `SupportGraphState`, `McpResilienceProperties`, all graph dependencies (mocked)

- [ ] **Step 1: Write the integration test**

Create `agents/src/test/java/com/example/agents/graph/SupportGraphMcpFailureTest.java`:

```java
package com.example.agents.graph;

import com.example.agents.mcp.McpResilienceProperties;
import com.example.agents.mcp.McpToolRouter;
import com.example.agents.mcp.ResilientToolCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SupportGraphMcpFailureTest {

    @Test
    @DisplayName("ResilientToolCallback returns MCP_UNAVAILABLE and tracks failures correctly")
    void resilientCallback_failureTracking_endToEnd() {
        // Arrange
        var delegate = mock(ToolCallback.class);
        var toolDef = mock(ToolDefinition.class);
        when(toolDef.name()).thenReturn("getOrder");
        when(delegate.getToolDefinition()).thenReturn(toolDef);
        when(delegate.call(anyString())).thenThrow(new RuntimeException("MCP server unreachable"));

        var props = new McpResilienceProperties();
        props.setRetryMaxAttempts(1);
        props.setRetryInitialBackoffMs(10);
        props.setCircuitBreakerFailureThreshold(5);
        props.setCircuitBreakerResetTimeoutMs(100);
        props.setMaxFailuresBeforeEscalation(2);

        var resilient = new ResilientToolCallback(delegate, props);

        // Act — simulate 2 failed tool calls
        String result1 = resilient.call("{\"orderId\":\"abc\"}");
        String result2 = resilient.call("{\"orderId\":\"abc\"}");

        // Assert
        assertThat(result1).startsWith("MCP_UNAVAILABLE:");
        assertThat(result2).startsWith("MCP_UNAVAILABLE:");
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(2);

        // Verify: 2 >= maxFailuresBeforeEscalation (2) triggers escalation
        assertThat(resilient.getConsecutiveFailures() >= props.getMaxFailuresBeforeEscalation()).isTrue();
    }

    @Test
    @DisplayName("Success after failures resets counter")
    void resilientCallback_successResetsCounter() {
        var delegate = mock(ToolCallback.class);
        var toolDef = mock(ToolDefinition.class);
        when(toolDef.name()).thenReturn("getOrder");
        when(delegate.getToolDefinition()).thenReturn(toolDef);

        // First call: all retries fail. Second call: succeeds
        when(delegate.call(anyString()))
            .thenThrow(new RuntimeException("fail"))
            .thenThrow(new RuntimeException("fail"))
            .thenReturn("Order Details: ...");

        var props = new McpResilienceProperties();
        props.setRetryMaxAttempts(1);
        props.setRetryInitialBackoffMs(10);
        props.setCircuitBreakerFailureThreshold(5);
        props.setCircuitBreakerResetTimeoutMs(100);
        props.setMaxFailuresBeforeEscalation(3);

        var resilient = new ResilientToolCallback(delegate, props);

        resilient.call("{}"); // fails (2 attempts exhausted)
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(1);

        String result = resilient.call("{}"); // succeeds
        assertThat(result).isEqualTo("Order Details: ...");
        assertThat(resilient.getConsecutiveFailures()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :agents:test --tests "com.example.agents.graph.SupportGraphMcpFailureTest" --info`
Expected: All 2 tests PASS.

- [ ] **Step 3: Run all agents tests**

Run: `./gradlew :agents:test --info`
Expected: All tests PASS (ResilientToolCallbackTest + McpToolRouterTest + IntentClassifierTest + SupportGraphMcpFailureTest).

- [ ] **Step 4: Commit**

```bash
git add agents/src/test/java/com/example/agents/graph/SupportGraphMcpFailureTest.java
git commit -m "test: add MCP failure escalation integration tests"
```

---

### Task 6: Full Build Verification + Cleanup

**Files:**
- Modify: `agents/build.gradle` (verify ecommerce dep is still needed or can be removed)

**Interfaces:**
- Consumes: All previous tasks
- Produces: Clean build, all tests passing

- [ ] **Step 1: Check if ecommerce dependency is still needed in agents module**

Run: `grep -r "com.example.ecommerce" agents/src/main/java/ --include="*.java"`

If the only hits are in the deleted files (which are now gone), the `implementation project(':ecommerce')` dependency can be removed from `agents/build.gradle`. If there are remaining references (e.g., exception classes used elsewhere), keep the dependency.

- [ ] **Step 2: Remove ecommerce dependency if unused**

If Step 1 shows no remaining references, edit `agents/build.gradle` line 8 — remove:

```groovy
implementation project(':ecommerce')
```

- [ ] **Step 3: Full build**

Run: `./gradlew clean build --info`
Expected: BUILD SUCCESSFUL. All modules compile and tests pass.

- [ ] **Step 4: Verify the application starts**

Run: `./gradlew :api:bootRun` (or check that the Spring context loads without errors related to missing beans)

Verify in logs:
- `McpToolRouter` logs `MCP tools available: [...]` (if MCP server is running) or `No MCP tool providers found` (if not)
- No `NoSuchBeanDefinitionException` for `OrderTools` or `RefundTools`
- `SupportGraph` initializes without errors

- [ ] **Step 5: Commit final cleanup**

```bash
git add -A
git commit -m "chore: remove unused ecommerce dependency from agents module"
```

(Only if the dependency was removed in Step 2.)

---

Plan complete and saved to `docs/superpowers/plans/2026-06-22-mcp-first-agent-overhaul.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
