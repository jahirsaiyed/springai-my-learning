# User-Customer Mapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Link authenticated users to ecommerce customers so order/refund tools return real data for the logged-in user.

**Architecture:** A `user_customer_mappings` table in the `ecommerce` schema maps `public.users.id` (UUID) to `ecommerce.customers.customer_id` (VARCHAR). A `CustomerResolver` service in the ecommerce module resolves the mapping. Controllers resolve the ecommerce customer ID and pass it as a separate `String ecomCustomerId` through the orchestrator → graph → agent system prompt, keeping the existing UUID `customerId` for memory operations. An admin CRUD endpoint manages mappings.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring Data JPA, Flyway, JUnit 5 + Mockito

---

## File Structure

| File | Module | Action | Responsibility |
|---|---|---|---|
| `api/src/main/resources/db/migration/V8__create_user_customer_mappings.sql` | api | CREATE | Flyway migration |
| `ecommerce/src/main/java/com/example/ecommerce/entity/UserCustomerMapping.java` | ecommerce | CREATE | JPA entity |
| `ecommerce/src/main/java/com/example/ecommerce/repository/UserCustomerMappingRepository.java` | ecommerce | CREATE | Spring Data repo |
| `ecommerce/src/main/java/com/example/ecommerce/service/CustomerResolver.java` | ecommerce | CREATE | Resolution service |
| `ecommerce/src/test/java/com/example/ecommerce/service/CustomerResolverTest.java` | ecommerce | CREATE | Unit test |
| `agents/src/main/java/com/example/agents/graph/SupportGraphState.java` | agents | MODIFY | Add `ECOM_CUSTOMER_ID` field |
| `agents/src/main/java/com/example/agents/graph/SupportGraph.java` | agents | MODIFY | Accept + propagate `ecomCustomerId`, inject into agent system prompts |
| `agents/src/main/java/com/example/agents/orchestrator/OrchestratorAgent.java` | agents | MODIFY | Accept `ecomCustomerId` parameter |
| `agents/src/main/java/com/example/agents/orchestrator/StreamingOrchestratorAgent.java` | agents | MODIFY | Accept `ecomCustomerId` parameter |
| `api/src/main/java/com/example/api/controller/ChatController.java` | api | MODIFY | Inject `CustomerResolver`, resolve before calling orchestrator |
| `api/src/main/java/com/example/api/controller/ChatStreamController.java` | api | MODIFY | Same |
| `api/src/main/java/com/example/api/websocket/ChatWebSocketHandler.java` | api | MODIFY | Same |
| `admin/src/main/java/com/example/admin/controller/UserCustomerMappingController.java` | admin | CREATE | Admin CRUD endpoint |
| `ecommerce/src/main/java/com/example/ecommerce/seed/OlistDataSeeder.java` | ecommerce | MODIFY | Seed test mappings |

---

### Task 1: Flyway Migration

**Files:**
- Create: `api/src/main/resources/db/migration/V8__create_user_customer_mappings.sql`

- [ ] **Step 1: Create the migration file**

```sql
CREATE TABLE ecommerce.user_customer_mappings (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES public.users(id),
    customer_id VARCHAR(64) NOT NULL UNIQUE REFERENCES ecommerce.customers(customer_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ucm_user ON ecommerce.user_customer_mappings(user_id);
CREATE INDEX idx_ucm_customer ON ecommerce.user_customer_mappings(customer_id);
```

- [ ] **Step 2: Verify migration compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add api/src/main/resources/db/migration/V8__create_user_customer_mappings.sql
git commit -m "feat: add V8 migration for user_customer_mappings table"
```

---

### Task 2: JPA Entity + Repository

**Files:**
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/UserCustomerMapping.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/UserCustomerMappingRepository.java`

- [ ] **Step 1: Create the JPA entity**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_customer_mappings", schema = "ecommerce")
public class UserCustomerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "customer_id", nullable = false, unique = true, length = 64)
    private String customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    protected UserCustomerMapping() {}

    public UserCustomerMapping(UUID userId, String customerId) {
        this.userId = userId;
        this.customerId = customerId;
    }

    public Integer getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCustomerId() { return customerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: Create the repository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.UserCustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCustomerMappingRepository extends JpaRepository<UserCustomerMapping, Integer> {

    Optional<UserCustomerMapping> findByUserId(UUID userId);

    Optional<UserCustomerMapping> findByCustomerId(String customerId);

    boolean existsByUserId(UUID userId);

    boolean existsByCustomerId(String customerId);
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :ecommerce:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/entity/UserCustomerMapping.java \
       ecommerce/src/main/java/com/example/ecommerce/repository/UserCustomerMappingRepository.java
git commit -m "feat: add UserCustomerMapping entity and repository"
```

---

### Task 3: CustomerResolver Service + Test

**Files:**
- Create: `ecommerce/src/main/java/com/example/ecommerce/service/CustomerResolver.java`
- Create: `ecommerce/src/test/java/com/example/ecommerce/service/CustomerResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.UserCustomerMapping;
import com.example.ecommerce.repository.UserCustomerMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerResolverTest {

    @Mock
    private UserCustomerMappingRepository mappingRepository;

    private CustomerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CustomerResolver(mappingRepository);
    }

    @Test
    void resolve_existingMapping_returnsCustomerId() {
        var userId = UUID.randomUUID();
        var mapping = new UserCustomerMapping(userId, "abc123def456");
        when(mappingRepository.findByUserId(userId)).thenReturn(Optional.of(mapping));

        var result = resolver.resolve(userId);

        assertThat(result).isPresent().contains("abc123def456");
    }

    @Test
    void resolve_noMapping_returnsEmpty() {
        var userId = UUID.randomUUID();
        when(mappingRepository.findByUserId(userId)).thenReturn(Optional.empty());

        var result = resolver.resolve(userId);

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ecommerce:test --tests "com.example.ecommerce.service.CustomerResolverTest" -i`
Expected: FAIL — `CustomerResolver` class does not exist

- [ ] **Step 3: Write the implementation**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.UserCustomerMapping;
import com.example.ecommerce.repository.UserCustomerMappingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerResolver {

    private final UserCustomerMappingRepository mappingRepository;

    public CustomerResolver(UserCustomerMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    public Optional<String> resolve(UUID userId) {
        return mappingRepository.findByUserId(userId)
                .map(UserCustomerMapping::getCustomerId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ecommerce:test --tests "com.example.ecommerce.service.CustomerResolverTest" -i`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/service/CustomerResolver.java \
       ecommerce/src/test/java/com/example/ecommerce/service/CustomerResolverTest.java
git commit -m "feat: add CustomerResolver service with tests"
```

---

### Task 4: Add ecomCustomerId to Graph State + SupportGraph

**Files:**
- Modify: `agents/src/main/java/com/example/agents/graph/SupportGraphState.java`
- Modify: `agents/src/main/java/com/example/agents/graph/SupportGraph.java`

- [ ] **Step 1: Add ECOM_CUSTOMER_ID to SupportGraphState**

In `SupportGraphState.java`, add the constant and accessor:

```java
// Add after line 26 (existing constants):
public static final String ECOM_CUSTOMER_ID = "ecomCustomerId";
```

```java
// Add after the customerId() method (after line 45):
public String ecomCustomerId() {
    return this.<String>value(ECOM_CUSTOMER_ID).orElse(null);
}
```

- [ ] **Step 2: Update SupportGraph.invoke to accept ecomCustomerId**

In `SupportGraph.java`, change the `invoke` method signature (line 220):

From:
```java
public GraphResult invoke(UUID tenantId, UUID customerId, UUID conversationId,
                          String userMessage, int turnCount) {
```

To:
```java
public GraphResult invoke(UUID tenantId, UUID customerId, UUID conversationId,
                          String userMessage, int turnCount, String ecomCustomerId) {
```

And add it to the inputs map (after line 231):

From:
```java
var inputs = Map.<String, Object>of(
    "messages", new UserMessage(userMessage),
    SupportGraphState.TENANT_ID, tenantId,
    SupportGraphState.CUSTOMER_ID, customerId,
    SupportGraphState.CONVERSATION_ID, conversationId,
    SupportGraphState.TURN_COUNT, turnCount
);
```

To:
```java
var inputs = new HashMap<String, Object>();
inputs.put("messages", new UserMessage(userMessage));
inputs.put(SupportGraphState.TENANT_ID, tenantId);
inputs.put(SupportGraphState.CUSTOMER_ID, customerId);
inputs.put(SupportGraphState.CONVERSATION_ID, conversationId);
inputs.put(SupportGraphState.TURN_COUNT, turnCount);
if (ecomCustomerId != null) {
    inputs.put(SupportGraphState.ECOM_CUSTOMER_ID, ecomCustomerId);
}
```

Add `import java.util.HashMap;` if not already present.

- [ ] **Step 3: Inject ecomCustomerId into agent prompts**

In `SupportGraph.java`, update the `callAgent` method (line 349) to include the ecommerce customer ID in the conversation context. Add a user-level context message before calling the agent:

From:
```java
private Map<String, Object> callAgent(ChatClient client, SupportGraphState state, String agentName) {
    var messages = state.messages();
    var prompt = client.prompt();
    if (messages != null && !messages.isEmpty()) {
        prompt.messages(messages);
    }
    String response = prompt.call().content();
```

To:
```java
private Map<String, Object> callAgent(ChatClient client, SupportGraphState state, String agentName) {
    var messages = state.messages();
    var prompt = client.prompt();
    if (messages != null && !messages.isEmpty()) {
        prompt.messages(messages);
    }

    // Inject ecommerce customer ID as system context for order/refund tools
    String ecomCustId = state.ecomCustomerId();
    if (ecomCustId != null) {
        prompt.system(s -> s.text("The current user's ecommerce customer ID is: " + ecomCustId
                + ". Use this ID when looking up orders, refunds, or shipments."));
    } else if ("ORDER".equals(agentName) || "REFUND".equals(agentName)) {
        prompt.system(s -> s.text("This user has no linked ecommerce account. "
                + "If they ask about orders or refunds, inform them that their account is not linked "
                + "and suggest they contact an admin to link their account."));
    }

    String response = prompt.call().content();
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :agents:compileJava`
Expected: FAIL — `OrchestratorAgent` and `StreamingOrchestratorAgent` call `invoke` with old signature. This is expected and will be fixed in Task 5.

- [ ] **Step 5: Commit**

```bash
git add agents/src/main/java/com/example/agents/graph/SupportGraphState.java \
       agents/src/main/java/com/example/agents/graph/SupportGraph.java
git commit -m "feat: add ecomCustomerId to graph state and agent prompts"
```

---

### Task 5: Update Orchestrators to Pass ecomCustomerId

**Files:**
- Modify: `agents/src/main/java/com/example/agents/orchestrator/OrchestratorAgent.java`
- Modify: `agents/src/main/java/com/example/agents/orchestrator/StreamingOrchestratorAgent.java`

- [ ] **Step 1: Update OrchestratorAgent**

Change the method signatures to accept `String ecomCustomerId`:

```java
public AgentResponse startConversation(UUID tenantId, UUID customerId,
                                        Channel channel, String userMessage,
                                        String ecomCustomerId) {
    Conversation conversation = memoryManager.episodic()
        .startConversation(tenantId, customerId, channel);

    return processMessage(tenantId, customerId, conversation.getId(), userMessage, 1, ecomCustomerId);
}

public AgentResponse continueConversation(UUID tenantId, UUID customerId,
                                           UUID conversationId, String userMessage,
                                           String ecomCustomerId) {
    var messages = memoryManager.episodic().getRecentMessages(conversationId, 100);
    int turnCount = messages.size() / 2 + 1;

    return processMessage(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);
}

private AgentResponse processMessage(UUID tenantId, UUID customerId,
                                      UUID conversationId, String userMessage,
                                      int turnCount, String ecomCustomerId) {
    var result = supportGraph.invoke(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);
    // ... rest unchanged
```

- [ ] **Step 2: Update StreamingOrchestratorAgent**

Same pattern — add `String ecomCustomerId` to all method signatures:

```java
public Flux<StreamEvent> startConversationStream(UUID tenantId, UUID customerId,
                                                  Channel channel, String userMessage,
                                                  String ecomCustomerId) {
    Conversation conversation = memoryManager.episodic()
        .startConversation(tenantId, customerId, channel);

    return processStream(tenantId, customerId, conversation.getId(), userMessage, 1, ecomCustomerId);
}

public Flux<StreamEvent> continueConversationStream(UUID tenantId, UUID customerId,
                                                     UUID conversationId, String userMessage,
                                                     String ecomCustomerId) {
    var messages = memoryManager.episodic().getRecentMessages(conversationId, 100);
    int turnCount = messages.size() / 2 + 1;

    return processStream(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);
}

private Flux<StreamEvent> processStream(UUID tenantId, UUID customerId,
                                         UUID conversationId, String userMessage,
                                         int turnCount, String ecomCustomerId) {
    return Flux.defer(() -> {
        var result = supportGraph.invoke(tenantId, customerId, conversationId, userMessage, turnCount, ecomCustomerId);
        // ... rest unchanged
```

- [ ] **Step 3: Verify agents module compiles**

Run: `./gradlew :agents:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add agents/src/main/java/com/example/agents/orchestrator/OrchestratorAgent.java \
       agents/src/main/java/com/example/agents/orchestrator/StreamingOrchestratorAgent.java
git commit -m "feat: propagate ecomCustomerId through orchestrator agents"
```

---

### Task 6: Wire CustomerResolver into Controllers

**Files:**
- Modify: `api/src/main/java/com/example/api/controller/ChatController.java`
- Modify: `api/src/main/java/com/example/api/controller/ChatStreamController.java`
- Modify: `api/src/main/java/com/example/api/websocket/ChatWebSocketHandler.java`

- [ ] **Step 1: Update ChatController**

Add `CustomerResolver` as a constructor dependency and resolve the ecommerce customer ID:

```java
package com.example.api.controller;

import com.example.agents.AgentResponse;
import com.example.agents.orchestrator.OrchestratorAgent;
import com.example.core.tenant.TenantContext;
import com.example.ecommerce.service.CustomerResolver;
import com.example.api.security.AuthenticatedUser;
import com.example.memory.episodic.Channel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OrchestratorAgent orchestrator;
    private final CustomerResolver customerResolver;

    public ChatController(OrchestratorAgent orchestrator, CustomerResolver customerResolver) {
        this.orchestrator = orchestrator;
        this.customerResolver = customerResolver;
    }

    @PostMapping("/start")
    public ResponseEntity<ChatResponse> startConversation(
            @Valid @RequestBody StartChatRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        var tenant = TenantContext.require();
        Channel channel = request.channel() != null
            ? Channel.valueOf(request.channel().toUpperCase())
            : Channel.WEB;

        String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);

        AgentResponse response = orchestrator.startConversation(
            tenant.getId(), user.id(), channel, request.message(), ecomCustomerId);

        return ResponseEntity.ok(ChatResponse.from(response));
    }

    @PostMapping("/{conversationId}")
    public ResponseEntity<ChatResponse> continueConversation(
            @PathVariable UUID conversationId,
            @Valid @RequestBody MessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        var tenant = TenantContext.require();
        String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);

        AgentResponse response = orchestrator.continueConversation(
            tenant.getId(), user.id(), conversationId, request.message(), ecomCustomerId);

        return ResponseEntity.ok(ChatResponse.from(response));
    }

    // resolveConversation, records — unchanged
```

- [ ] **Step 2: Update ChatStreamController**

Add `CustomerResolver` dependency and resolve before each orchestrator call:

```java
private final StreamingOrchestratorAgent streamingOrchestrator;
private final CustomerResolver customerResolver;

public ChatStreamController(StreamingOrchestratorAgent streamingOrchestrator,
                              CustomerResolver customerResolver) {
    this.streamingOrchestrator = streamingOrchestrator;
    this.customerResolver = customerResolver;
}
```

In `startConversationStream`:
```java
String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);

return streamingOrchestrator.startConversationStream(
    tenant.getId(), user.id(), channel, request.message(), ecomCustomerId
).onErrorResume(e -> Flux.just(StreamEvent.error(e.getMessage())));
```

In `continueConversationStream`:
```java
String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);

return streamingOrchestrator.continueConversationStream(
    tenant.getId(), user.id(), conversationId, request.message(), ecomCustomerId
).onErrorResume(e -> Flux.just(StreamEvent.error(e.getMessage())));
```

- [ ] **Step 3: Update ChatWebSocketHandler**

Add `CustomerResolver` to the constructor:

```java
private final CustomerResolver customerResolver;

public ChatWebSocketHandler(OrchestratorAgent orchestrator,
                             StreamingOrchestratorAgent streamingOrchestrator,
                             SimpMessagingTemplate messagingTemplate,
                             TenantRepository tenantRepository,
                             CustomerResolver customerResolver) {
    this.orchestrator = orchestrator;
    this.streamingOrchestrator = streamingOrchestrator;
    this.messagingTemplate = messagingTemplate;
    this.tenantRepository = tenantRepository;
    this.customerResolver = customerResolver;
}
```

In `startConversation`:
```java
String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);

streamingOrchestrator.startConversationStream(
    tenant.getId(), user.id(), channel, request.message(), ecomCustomerId
).subscribe(
```

In `sendMessage`:
```java
String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);

streamingOrchestrator.continueConversationStream(
    tenant.getId(), user.id(), conversationId, request.message(), ecomCustomerId
).subscribe(
```

- [ ] **Step 4: Verify full build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/example/api/controller/ChatController.java \
       api/src/main/java/com/example/api/controller/ChatStreamController.java \
       api/src/main/java/com/example/api/websocket/ChatWebSocketHandler.java
git commit -m "feat: wire CustomerResolver into chat controllers"
```

---

### Task 7: Admin CRUD Endpoint

**Files:**
- Create: `admin/src/main/java/com/example/admin/controller/UserCustomerMappingController.java`

- [ ] **Step 1: Create the admin controller**

```java
package com.example.admin.controller;

import com.example.ecommerce.entity.UserCustomerMapping;
import com.example.ecommerce.repository.UserCustomerMappingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/user-customer-mappings")
public class UserCustomerMappingController {

    private final UserCustomerMappingRepository mappingRepository;

    public UserCustomerMappingController(UserCustomerMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @PostMapping
    public ResponseEntity<?> createMapping(@Valid @RequestBody CreateMappingRequest request) {
        try {
            var mapping = new UserCustomerMapping(request.userId(), request.customerId());
            var saved = mappingRepository.save(mapping);
            return ResponseEntity.status(HttpStatus.CREATED).body(MappingResponse.from(saved));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Mapping already exists or references invalid user/customer"));
        }
    }

    @GetMapping
    public ResponseEntity<List<MappingResponse>> listMappings() {
        var mappings = mappingRepository.findAll().stream()
                .map(MappingResponse::from)
                .toList();
        return ResponseEntity.ok(mappings);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable Integer id) {
        if (!mappingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        mappingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateMappingRequest(
            @NotNull UUID userId,
            @NotBlank String customerId
    ) {}

    public record MappingResponse(Integer id, UUID userId, String customerId, LocalDateTime createdAt) {
        public static MappingResponse from(UserCustomerMapping m) {
            return new MappingResponse(m.getId(), m.getUserId(), m.getCustomerId(), m.getCreatedAt());
        }
    }

    public record ErrorResponse(String error) {}
}
```

- [ ] **Step 2: Verify the admin module has ecommerce dependency**

Check `admin/build.gradle`. If `implementation project(':ecommerce')` is missing, add it.

- [ ] **Step 3: Verify full build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add admin/src/main/java/com/example/admin/controller/UserCustomerMappingController.java
git commit -m "feat: add admin CRUD for user-customer mappings"
```

---

### Task 8: Seed Test Mappings

**Files:**
- Modify: `ecommerce/src/main/java/com/example/ecommerce/seed/OlistDataSeeder.java`

- [ ] **Step 1: Add seedUserCustomerMappings method**

Add a new method call in `run()` after `synthesizeRefunds()`:

```java
seedUserCustomerMappings();
```

Add the method:

```java
private void seedUserCustomerMappings() {
    log.info("Seeding user-customer mappings...");

    // Get existing users from public.users
    var users = jdbc.queryForList("SELECT id FROM public.users");
    if (users.isEmpty()) {
        log.info("No users found in public.users — skipping mapping seed");
        return;
    }

    // Get random customer IDs that aren't already mapped
    var availableCustomers = jdbc.queryForList(
            "SELECT customer_id FROM ecommerce.customers " +
            "WHERE customer_id NOT IN (SELECT customer_id FROM ecommerce.user_customer_mappings) " +
            "ORDER BY RANDOM() LIMIT ?",
            String.class, users.size());

    int mapped = 0;
    for (int i = 0; i < users.size() && i < availableCustomers.size(); i++) {
        UUID userId = (UUID) users.get(i).get("id");
        String customerId = availableCustomers.get(i);
        try {
            jdbc.update(
                    "INSERT INTO ecommerce.user_customer_mappings (user_id, customer_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    userId, customerId);
            mapped++;
            log.info("Mapped user {} -> customer {}", userId, customerId);
        } catch (Exception e) {
            log.warn("Failed to map user {} -> customer {}: {}", userId, customerId, e.getMessage());
        }
    }
    log.info("Seeded {} user-customer mappings", mapped);
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :ecommerce:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/seed/OlistDataSeeder.java
git commit -m "feat: seed test user-customer mappings during data seeding"
```

---

### Task 9: Full Build + Verification

- [ ] **Step 1: Run full build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Commit any fixes if needed**

---

## Validation

```bash
# Full compilation
./gradlew compileJava

# Unit tests
./gradlew :ecommerce:test --tests "com.example.ecommerce.service.CustomerResolverTest" -i

# Full test suite
./gradlew test

# Run the app with seed profile to test migration + seeding
./gradlew :api:bootRun --args='--spring.profiles.active=seed'
```

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Migration V8 conflicts with another migration added concurrently | Low | Check for existing V8 before creating |
| `ChatClient.prompt().system()` call conflicts with default system prompt | Medium | Test with actual LLM to verify system prompts merge correctly |
| `ecommerce` module not on `admin` module classpath | Medium | Check and add `implementation project(':ecommerce')` to `admin/build.gradle` |

## Acceptance

- [ ] All tasks complete
- [ ] `CustomerResolver` has passing unit tests
- [ ] Full build passes (`./gradlew compileJava`)
- [ ] Migration runs without errors
- [ ] Admin CRUD endpoint creates/lists/deletes mappings
- [ ] Chat controllers resolve ecommerce customer ID before calling orchestrator
- [ ] Agent prompt includes ecommerce customer ID when available
- [ ] Agent prompt warns when no mapping exists (for order/refund queries)
- [ ] Test seed mappings created during seeding
