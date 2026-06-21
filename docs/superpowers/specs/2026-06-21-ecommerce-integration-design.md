# Ecommerce Integration Design

## Problem

The AI customer support agent currently uses DummyJSON as a mock ecommerce backend — 30 carts mapped as orders, 208 users, and order statuses derived from ID ranges. This is too shallow to properly test agent behavior in production-like conditions. We need a rich, realistic ecommerce dataset with thousands of products, orders, customers, and reviews.

## Decision Summary

- **Data source:** Olist Brazilian E-Commerce dataset from Kaggle (~100K orders, 33K products, 99K customers, 99K reviews, 103K payments)
- **Storage:** PostgreSQL `ecommerce` schema, loaded via Flyway migration + seed runner
- **Architecture:** Two new Gradle modules — `ecommerce` (library) and `ecommerce-mcp` (standalone Spring Boot app)
- **Agent integration:** `agents` module depends on `ecommerce` directly; tools refactored to use local DB queries
- **MCP server:** SSE transport on port 8081, 13 tools exposed
- **Backward compatibility:** DummyJSON kept as profile-toggled fallback

## Architecture

```
SpringAISample (monorepo)
├── core/
├── api/              (port 8080)
├── memory/
├── agents/           ──depends on──> ecommerce
├── admin/
├── ecommerce/        (new module — entities, repos, services, seed loader)
└── ecommerce-mcp/    (new module — Spring Boot app, SSE on port 8081, 13 MCP tools)
                      ──depends on──> ecommerce
```

### `ecommerce` module (library, no Spring Boot app)

Pure data layer shared by both `agents` and `ecommerce-mcp`:

- JPA entities mapping to the `ecommerce` schema
- Spring Data JPA repositories
- Service classes: `EcommerceOrderService`, `EcommerceProductService`, `EcommerceCustomerService`, `EcommerceRefundService`, `EcommerceReviewService`
- Seed data loader (`ApplicationRunner`, profile-gated)
- Flyway migration for schema creation

### `ecommerce-mcp` module (standalone Spring Boot app)

- Runs on port 8081
- SSE transport only (no stdio)
- Depends on `ecommerce` module
- Exposes 13 MCP tools backed by ecommerce services
- Own `application.yml` with DB connection to same PostgreSQL instance

### Dependency Graph

```
agents ──> ecommerce <── ecommerce-mcp
```

Both `agents` and `ecommerce-mcp` share the same PostgreSQL instance and `ecommerce` schema. No circular dependencies.

## Database Schema

Flyway migration V6 creates the `ecommerce` schema with 9 tables:

| Table | Rows | Key Columns |
|---|---|---|
| `customers` | 99K | customer_id, city, state, zip_code |
| `products` | 33K | product_id, category, weight_g, length_cm, height_cm, width_cm |
| `sellers` | 3K | seller_id, city, state, zip_code |
| `orders` | 99K | order_id, customer_id, status, purchase_timestamp, delivered_timestamp, estimated_delivery_timestamp |
| `order_items` | 112K | order_id, product_id, seller_id, price, freight_value |
| `order_payments` | 103K | order_id, payment_type, payment_installments, payment_value |
| `order_reviews` | 99K | order_id, review_score (1-5), review_comment_title, review_comment_message |
| `product_categories` | 71 | category_name_pt, category_name_en |
| `refunds` | synthetic | order_id, refund_id, amount, status, reason, created_at |

The `refunds` table is synthesized during seeding:
- Canceled orders get `COMPLETED` refunds
- Delivered orders with 1-2 star reviews get `PROCESSING` or `PENDING` refunds (randomized)

The `geolocation` table (1M rows) is skipped — not needed for support agent testing.

## Data Seeding

### Pipeline

1. **Manual download** — Olist CSVs from Kaggle (requires account), placed in `ecommerce/src/main/resources/seed/`
2. **Flyway migration V6** — creates the schema and tables
3. **Seed runner** — Spring `ApplicationRunner` enabled via `--spring.profiles.active=seed`:
   - Reads CSVs using `BufferedReader` (no external CSV library needed for simple Olist format)
   - Bulk-inserts via JDBC batch (JPA too slow for 100K+ rows)
   - Translates Portuguese categories to English using the translation table
   - Synthesizes refund records from canceled orders and low-review-score orders
4. **Idempotent** — seed runner checks if tables already have data before inserting

### CSV Files Required

Place these in `ecommerce/src/main/resources/seed/`:
- `olist_customers_dataset.csv`
- `olist_orders_dataset.csv`
- `olist_order_items_dataset.csv`
- `olist_order_payments_dataset.csv`
- `olist_order_reviews_dataset.csv`
- `olist_products_dataset.csv`
- `olist_sellers_dataset.csv`
- `product_category_name_translation.csv`

## MCP Tools

13 tools organized by domain, exposed via SSE on port 8081.

### Products

| Tool | Parameters | Returns |
|---|---|---|
| `search_products` | `query` (string), `category` (optional), `limit` (default 10) | List of matching products with id, name, category, price |
| `get_product` | `productId` (string) | Full product details: id, name, category, price, weight, dimensions, rating, review count |
| `list_categories` | none | All 71 categories with English names and product counts |

### Orders

| Tool | Parameters | Returns |
|---|---|---|
| `get_order` | `orderId` (string) | Order with status, items, payment, delivery dates, customer id |
| `list_customer_orders` | `customerId` (string), `limit` (default 10) | Customer's orders sorted by purchase date desc |
| `track_shipment` | `orderId` (string) | Estimated/actual delivery dates, current status, freight info |
| `cancel_order` | `orderId` (string), `reason` (string) | Success/failure based on order status (only pending/processing cancellable) |

### Refunds

| Tool | Parameters | Returns |
|---|---|---|
| `check_refund_eligibility` | `orderId` (string) | Eligibility status, policy details, eligible amount |
| `initiate_refund` | `orderId` (string), `amount` (string), `reason` (string) | Refund ID, status, expected completion |
| `get_refund_status` | `orderId` (string) | Refund status (pending/processing/completed), amount, dates |

### Customers

| Tool | Parameters | Returns |
|---|---|---|
| `get_customer` | `customerId` (string) | Customer details: id, city, state, order count |
| `search_customers` | `query` (string), `limit` (default 10) | Matching customers by city/state |

### Reviews

| Tool | Parameters | Returns |
|---|---|---|
| `get_order_reviews` | `orderId` (string) | Review score, title, comment for the order |
| `get_product_reviews` | `productId` (string), `limit` (default 10) | Reviews across all orders containing this product |

All tools return structured text following the existing `@Tool` convention. Errors use the `INVALID_*` / `NOT_FOUND` pattern already established in `OrderTools` and `RefundTools`.

## Agent Tool Refactoring

| Current | New |
|---|---|
| `DummyJsonClient` (HTTP to dummyjson.com) | `EcommerceOrderService`, `EcommerceProductService`, etc. (local DB) |
| `OrderTools` calls `dummyJson.getCart()` | `OrderTools` calls `orderService.getOrder()` |
| `RefundTools` calls `dummyJson.getCart()` | `RefundTools` calls `refundService.checkEligibility()` |
| Order status derived from cart ID ranges | Real `order_status` field from Olist data |
| 30 orders, 208 users | 99K orders, 99K customers, 33K products |

### Profile-Based Switching

- `olist` profile (default): agent tools use `ecommerce` services backed by local PostgreSQL
- `dummyjson` profile: agent tools use existing `DummyJsonClient` backed by dummyjson.com API
- Switching via `@ConditionalOnProperty` on service implementations
- Tool method signatures and `@Tool` annotations remain unchanged

## Package Structure

### `ecommerce` module

```
com.example.ecommerce/
├── entity/
│   ├── EcomCustomer.java
│   ├── EcomProduct.java
│   ├── EcomSeller.java
│   ├── EcomOrder.java
│   ├── EcomOrderItem.java
│   ├── EcomOrderPayment.java
│   ├── EcomOrderReview.java
│   ├── EcomProductCategory.java
│   └── EcomRefund.java
├── repository/
│   ├── EcomCustomerRepository.java
│   ├── EcomProductRepository.java
│   ├── EcomOrderRepository.java
│   ├── EcomOrderReviewRepository.java
│   └── EcomRefundRepository.java
├── service/
│   ├── EcommerceOrderService.java
│   ├── EcommerceProductService.java
│   ├── EcommerceCustomerService.java
│   ├── EcommerceRefundService.java
│   └── EcommerceReviewService.java
└── seed/
    └── OlistDataSeeder.java
```

### `ecommerce-mcp` module

```
com.example.ecommercemcp/
├── EcommerceMcpApplication.java
├── config/
│   └── McpServerConfig.java
└── tools/
    ├── McpProductTools.java
    ├── McpOrderTools.java
    ├── McpRefundTools.java
    ├── McpCustomerTools.java
    └── McpReviewTools.java
```

## Error Handling

All services throw domain-specific unchecked exceptions:
- `OrderNotFoundException` — order ID not found
- `CustomerNotFoundException` — customer ID not found
- `ProductNotFoundException` — product ID not found
- `RefundNotEligibleException` — order not eligible for refund
- `OrderNotCancellableException` — order already shipped/delivered

Tool methods catch these and return structured error strings matching the existing `INVALID_*` / `NOT_FOUND` convention.

## MCP Server Dependencies

The `ecommerce-mcp` module uses Spring AI's MCP Server support:
- `spring-ai-starter-mcp-server-webflux` for SSE transport
- Spring Boot 3.4.4 with WebFlux (separate from the main `api` module which uses servlet-based Spring MVC)
- MCP tools defined using `@Tool` annotations, registered via `ToolCallbackProvider`

## Testing Strategy

- Unit tests for all service classes using Mockito
- Integration tests for repositories using Testcontainers (PostgreSQL)
- MCP tool tests verifying correct tool registration and response format
- Seed loader test verifying idempotent loading with a small CSV subset
