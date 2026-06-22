# MCP-First Agent Overhaul Design

**Date**: 2026-06-22
**Status**: Approved

## Problem

The support agent has overlapping local and MCP tools for order/refund operations. When the LLM picks an MCP tool and the MCP server is unreachable, the agent returns vague "technical issue" responses. Additionally, the intent classifier loses context on follow-up turns (e.g., bare order IDs, single-word replies like "return").

## Goals

1. Make MCP the single source of truth for order/refund data (eliminate local tool duplication)
2. Add MCP resilience (retry, circuit breaker, escalation after N failures)
3. Context-aware intent classification using conversation history
4. Fix multi-turn context injection (remove UserMessage hack)

## Design

### 1. Remove Local Tool Duplication

**Delete** the local data provider layer:

- `agents/.../tools/OrderTools.java`
- `agents/.../tools/RefundTools.java`
- `agents/.../tools/OrderDataProvider.java`
- `agents/.../tools/RefundDataProvider.java`
- `agents/.../tools/olist/OlistOrderDataProvider.java`
- `agents/.../tools/olist/OlistRefundDataProvider.java`
- `agents/.../tools/dummyjson/DummyJsonOrderDataProvider.java`
- `agents/.../tools/dummyjson/DummyJsonRefundDataProvider.java`

**SupportGraph** ChatClient changes:

```java
// ORDER agent — MCP tools only
this.orderClient = ChatClient.builder(chatModel)
    .defaultSystem(orderSystemPrompt)
    .defaultTools(askUserQuestionTool)
    .defaultToolCallbacks(mcpToolRouter.getOrderAgentTools())
    .build();

// REFUND agent — MCP tools only
this.refundClient = ChatClient.builder(chatModel)
    .defaultSystem(refundSystemPrompt)
    .defaultTools(askUserQuestionTool)
    .defaultToolCallbacks(mcpToolRouter.getRefundAgentTools())
    .build();
```

Remove `OrderTools` and `RefundTools` from constructor injection.

### 2. MCP Resilience Layer

New class `ResilientToolCallback` wrapping each MCP `ToolCallback` with:

- **Retry**: Up to 2 retries with exponential backoff (500ms, 1s)
- **Circuit breaker**: Opens after 3 consecutive failures, half-open after 30s
- **Error response**: Returns a structured error string (e.g., `"MCP_UNAVAILABLE: The order system is temporarily unavailable."`) instead of throwing

`McpToolRouter.filterByNames` wraps each callback:

```java
private ToolCallback[] filterByNames(Set<String> names) {
    return names.stream()
        .map(mcpTools::get)
        .filter(Objects::nonNull)
        .map(cb -> new ResilientToolCallback(cb, retryConfig, circuitBreakerConfig))
        .toArray(ToolCallback[]::new);
}
```

**Conversation-level failure tracking**:

- New state field: `SupportGraphState.MCP_FAILURE_COUNT` (int)
- After each agent node, a conditional edge checks failure count
- If `mcp_failure_count >= app.mcp.max-failures-before-escalation` (default 3), route to ESCALATION_AGENT instead of POST_PROCESS

**Configuration** (`application.yml`):

```yaml
app:
  mcp:
    retry:
      max-attempts: 2
      initial-backoff-ms: 500
    circuit-breaker:
      failure-threshold: 3
      reset-timeout-ms: 30000
    max-failures-before-escalation: 3
```

**Config properties class**: `McpResilienceProperties` with `@ConfigurationProperties(prefix = "app.mcp")`.

### 3. Context-Aware Intent Classification

Change `IntentClassifier.classify` signature:

```java
public IntentClassification classify(String userMessage, List<Message> conversationHistory)
```

**Keyword classification** remains as fast path (unchanged). For ambiguous messages (no keyword match), the LLM classifier receives conversation context:

```
You are an intent classifier for a customer support system.
Given the conversation history and the latest customer message,
classify the intent into exactly ONE category:

ORDER - Order lookup, tracking, shipment status, delivery questions, order cancellation
REFUND - Refund requests, return requests, money back, overcharges, refund status
KNOWLEDGE - Policy questions, FAQ, product information, general inquiries
ESCALATION - Explicit request for human agent, complaints, unresolved frustration

CONVERSATION HISTORY:
{last 6 messages, role-labeled}

LATEST MESSAGE:
{userMessage}

Respond with ONLY the category name.
```

**Router node** passes state messages:

```java
private Map<String, Object> routerNode(SupportGraphState state) {
    var lastMsg = getLastUserMessageText(state);
    var intent = intentClassifier.classify(lastMsg, state.messages());
    ...
}
```

### 4. Multi-Turn Context Injection Fix

Replace the `UserMessage` hack in `callAgent` with system prompt interpolation at ChatClient construction time. Since `ecomCustomerId` varies per request, inject it per-call by appending to the system prompt:

```java
private Map<String, Object> callAgent(ChatClient client, SupportGraphState state, String agentName) {
    var messages = state.messages();
    var prompt = client.prompt();

    if (messages != null && !messages.isEmpty()) {
        prompt.messages(messages);
    }

    // Inject customer context via system() — appends to defaultSystem
    String ecomCustId = state.ecomCustomerId();
    if (ecomCustId != null) {
        prompt.system(s -> s.param("ecomCustomerId", ecomCustId));
    }

    String response = prompt.call().content();
    ...
}
```

Update defaultSystem prompts to include template placeholder:

```
You are an Order Support Agent...
{{#ecomCustomerId}}
The current user's ecommerce customer ID is: {ecomCustomerId}.
Use this when calling order/refund tools.
{{/ecomCustomerId}}
```

If Spring AI's ChatClient doesn't support conditional template blocks, fall back to building the system prompt string dynamically per-call using `prompt.system(systemPromptText)` with string concatenation — only when `ecomCustomerId` is present.

### 5. Graph Edge Changes

Current flow:
```
agent_node -> POST_PROCESS -> END
```

New flow:
```
agent_node -> failure_check (conditional)
  - mcp_failure_count >= N -> ESCALATION_AGENT
  - otherwise -> POST_PROCESS -> END
```

This is implemented as a conditional edge after each agent node rather than a separate node, to avoid adding graph complexity.

### 6. Files Changed

| File | Action |
|---|---|
| `OrderTools.java` | Delete |
| `RefundTools.java` | Delete |
| `OrderDataProvider.java` | Delete |
| `RefundDataProvider.java` | Delete |
| `olist/OlistOrderDataProvider.java` | Delete |
| `olist/OlistRefundDataProvider.java` | Delete |
| `dummyjson/DummyJsonOrderDataProvider.java` | Delete |
| `dummyjson/DummyJsonRefundDataProvider.java` | Delete |
| `ResilientToolCallback.java` | Create |
| `McpResilienceProperties.java` | Create |
| `McpToolRouter.java` | Modify (wrap callbacks, inject config) |
| `SupportGraph.java` | Modify (remove local tools, add failure routing, fix context injection) |
| `SupportGraphState.java` | Modify (add `mcp_failure_count`) |
| `IntentClassifier.java` | Modify (accept conversation history) |
| `application.yml` | Modify (add MCP resilience config) |
| `agents/build.gradle` | Verify (ecommerce dep may be removable if no other local usage) |

### 7. Testing

- Unit tests for `ResilientToolCallback` (retry, circuit breaker, failure counting)
- Unit tests for `IntentClassifier` with conversation history (follow-up order ID, "return" after order context)
- Integration test for failure escalation flow (mock MCP failures, verify auto-escalation after N)
- Verify `McpToolRouter` wraps callbacks correctly
