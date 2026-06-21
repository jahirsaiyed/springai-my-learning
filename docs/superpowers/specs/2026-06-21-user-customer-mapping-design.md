# User-Customer Mapping Design

## Problem

The `ChatController` passes `user.id()` (auth UUID from `public.users`) as `customerId` to the orchestrator. But Olist ecommerce customers use string IDs like `06b8999e2fba1a1fbc88172c00ba8bc7` in `ecommerce.customers`. There is no link between the two domains — every order/refund/shipment lookup returns empty results for authenticated users.

## Solution

A `user_customer_mappings` table in the `ecommerce` schema providing a strict 1:1 mapping between `public.users.id` (UUID) and `ecommerce.customers.customer_id` (VARCHAR). A `CustomerResolver` service resolves the authenticated user's UUID to an ecommerce customer ID before passing it to the orchestrator.

## Schema

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

Both `user_id` and `customer_id` have UNIQUE constraints enforcing 1:1 mapping.

## Resolution Flow

1. User sends chat message via REST/SSE/WebSocket
2. Controller extracts `user.id()` (UUID) from `@AuthenticationPrincipal`
3. `CustomerResolver.resolve(userId)` looks up the mapping table
4. Returns `Optional<String>` — the ecommerce `customer_id` or empty
5. Controller passes the resolved customer ID string (or null) to orchestrator
6. Orchestrator/graph propagates it to agent context and tools
7. Tools use the string customer ID for order/refund lookups

## Components

### New Files

| File | Module | Purpose |
|---|---|---|
| `V8__create_user_customer_mappings.sql` | api (migration) | Flyway migration for the mapping table |
| `UserCustomerMapping.java` | ecommerce | JPA entity |
| `UserCustomerMappingRepository.java` | ecommerce | Spring Data repository |
| `CustomerResolver.java` | ecommerce | Service: `resolve(UUID userId) -> Optional<String>` |

### Modified Files

| File | Module | Change |
|---|---|---|
| `ChatController.java` | api | Use `CustomerResolver` to resolve user ID before calling orchestrator |
| `ChatStreamController.java` | api | Same — resolve before streaming orchestrator call |
| `ChatWebSocketHandler.java` | api | Same — resolve before WebSocket orchestrator call |
| `OrchestratorAgent.java` | agents | Change `customerId` parameter from `UUID` to `String` |
| `StreamingOrchestratorAgent.java` | agents | Change `customerId` parameter from `UUID` to `String` |
| `SupportGraph.java` | agents | Change `customerId` in invoke/state from `UUID` to `String` |
| `SupportGraphState.java` | agents | Change `customerId()` return type from `UUID` to `String` |
| `AgentContext.java` | agents | Change `customerId` field from `UUID` to `String` |

### Admin API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/admin/user-customer-mappings` | POST | Create a mapping (body: `{userId, customerId}`) |
| `/api/admin/user-customer-mappings` | GET | List all mappings (paginated) |
| `/api/admin/user-customer-mappings/{id}` | DELETE | Remove a mapping |

### Test Seed Data

The `OlistDataSeeder` adds test mappings for any existing users in `public.users`, assigning them random Olist customer IDs from `ecommerce.customers`. This runs only in `seed` profile.

## Error Handling

- **No mapping found**: Orchestrator receives `null` as customerId. The agent can still handle FAQ, knowledge, and escalation queries. Order/refund tools detect null and return a message like: "I don't have access to your order history. Please contact support to link your account."
- **Mapping to nonexistent customer**: FK constraint prevents this at the DB level.
- **Duplicate mapping attempt**: UNIQUE constraints return a clear constraint violation error, surfaced as a 409 Conflict by the admin API.

## Type Change: customerId UUID -> String

Currently `AgentContext`, `OrchestratorAgent`, `SupportGraph`, and `SupportGraphState` all use `UUID` for `customerId`. This was a mismatch even before — the tools all accept `String customerId`. The Olist customer IDs are 32-char hex strings, not UUIDs. This spec changes the type to `String` throughout the agent layer to match reality.

The API controllers remain the boundary where resolution happens: `UUID userId` in, `String customerId` out.

## Out of Scope

- Self-service customer linking (user provides email/order to auto-link)
- Multiple customer profiles per user
- Customer profile UI in the chat frontend
