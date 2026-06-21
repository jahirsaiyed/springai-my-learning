# Ecommerce Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace DummyJSON with the Olist Brazilian E-Commerce dataset loaded into PostgreSQL, refactor agent tools, and expose 13 MCP tools via an SSE server.

**Architecture:** Two new Gradle modules — `ecommerce` (shared data layer with JPA entities, repos, services) and `ecommerce-mcp` (standalone Spring Boot app on port 8081 with SSE MCP server). The `agents` module depends on `ecommerce` directly. Profile-based switching keeps DummyJSON as fallback.

**Tech Stack:** Spring Boot 3.4.4, Spring Data JPA, Spring AI 1.0.0 MCP Server (WebFlux SSE), PostgreSQL, Flyway, Java 21

**Spec:** `docs/superpowers/specs/2026-06-21-ecommerce-integration-design.md`

---

## Task 1: Gradle Module Scaffolding

**Files:**
- Modify: `settings.gradle`
- Create: `ecommerce/build.gradle`
- Create: `ecommerce-mcp/build.gradle`

- [ ] **Step 1: Update settings.gradle**

```groovy
rootProject.name = 'spring-ai-sample'

include 'core'
include 'memory'
include 'agents'
include 'api'
include 'admin'
include 'ecommerce'
include 'ecommerce-mcp'
```

- [ ] **Step 2: Create ecommerce/build.gradle**

```groovy
plugins {
    id 'java-library'
}

dependencies {
    api 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
    api 'org.flywaydb:flyway-core'
    api 'org.flywaydb:flyway-database-postgresql'
}
```

- [ ] **Step 3: Create ecommerce-mcp/build.gradle**

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation project(':ecommerce')

    implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webflux'
}
```

- [ ] **Step 4: Add ecommerce dependency to agents/build.gradle**

Add `implementation project(':ecommerce')` to the existing dependencies block:

```groovy
plugins {
    id 'java-library'
}

dependencies {
    api project(':core')
    implementation project(':memory')
    implementation project(':ecommerce')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.ai:spring-ai-starter-model-openai'
    implementation 'org.bsc.langgraph4j:langgraph4j-spring-ai:1.8.19'
}
```

- [ ] **Step 5: Create directory structures**

Run:
```bash
mkdir -p ecommerce/src/main/java/com/example/ecommerce/{entity,repository,service,seed,exception}
mkdir -p ecommerce/src/main/resources/{db/migration,seed}
mkdir -p ecommerce/src/test/java/com/example/ecommerce/service
mkdir -p ecommerce-mcp/src/main/java/com/example/ecommercemcp/{config,tools}
mkdir -p ecommerce-mcp/src/main/resources
mkdir -p ecommerce-mcp/src/test/java/com/example/ecommercemcp
```

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add settings.gradle ecommerce/ ecommerce-mcp/ agents/build.gradle
git commit -m "feat: scaffold ecommerce and ecommerce-mcp Gradle modules"
```

---

## Task 2: Flyway Migration — Ecommerce Schema

**Files:**
- Create: `ecommerce/src/main/resources/db/migration/V6__create_ecommerce_schema.sql`

Note: Olist uses string hashes for all IDs (32-char hex). Order status values: `delivered`, `shipped`, `canceled`, `unavailable`, `invoiced`, `processing`, `created`, `approved`. Payment types: `credit_card`, `boleto`, `voucher`, `debit_card`, `not_defined`.

- [ ] **Step 1: Create migration SQL**

```sql
-- Ecommerce schema: Olist Brazilian E-Commerce dataset tables

CREATE SCHEMA IF NOT EXISTS ecommerce;

-- Product category translations (Portuguese to English)
CREATE TABLE ecommerce.product_categories (
    category_name_pt VARCHAR(100) PRIMARY KEY,
    category_name_en VARCHAR(100) NOT NULL
);

-- Customers
CREATE TABLE ecommerce.customers (
    customer_id VARCHAR(64) PRIMARY KEY,
    customer_unique_id VARCHAR(64) NOT NULL,
    zip_code_prefix VARCHAR(10),
    city VARCHAR(255),
    state VARCHAR(5)
);

CREATE INDEX idx_ecom_customers_unique ON ecommerce.customers(customer_unique_id);
CREATE INDEX idx_ecom_customers_city ON ecommerce.customers(city);
CREATE INDEX idx_ecom_customers_state ON ecommerce.customers(state);

-- Sellers
CREATE TABLE ecommerce.sellers (
    seller_id VARCHAR(64) PRIMARY KEY,
    zip_code_prefix VARCHAR(10),
    city VARCHAR(255),
    state VARCHAR(5)
);

-- Products
CREATE TABLE ecommerce.products (
    product_id VARCHAR(64) PRIMARY KEY,
    category_name_pt VARCHAR(100) REFERENCES ecommerce.product_categories(category_name_pt),
    name_length INTEGER,
    description_length INTEGER,
    photos_qty INTEGER,
    weight_g INTEGER,
    length_cm INTEGER,
    height_cm INTEGER,
    width_cm INTEGER
);

CREATE INDEX idx_ecom_products_category ON ecommerce.products(category_name_pt);

-- Orders
CREATE TABLE ecommerce.orders (
    order_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES ecommerce.customers(customer_id),
    status VARCHAR(30) NOT NULL,
    purchase_timestamp TIMESTAMP,
    approved_at TIMESTAMP,
    delivered_carrier_date TIMESTAMP,
    delivered_customer_date TIMESTAMP,
    estimated_delivery_date TIMESTAMP
);

CREATE INDEX idx_ecom_orders_customer ON ecommerce.orders(customer_id);
CREATE INDEX idx_ecom_orders_status ON ecommerce.orders(status);

-- Order items
CREATE TABLE ecommerce.order_items (
    order_id VARCHAR(64) NOT NULL REFERENCES ecommerce.orders(order_id),
    order_item_id INTEGER NOT NULL,
    product_id VARCHAR(64) NOT NULL REFERENCES ecommerce.products(product_id),
    seller_id VARCHAR(64) NOT NULL REFERENCES ecommerce.sellers(seller_id),
    shipping_limit_date TIMESTAMP,
    price NUMERIC(10, 2) NOT NULL,
    freight_value NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (order_id, order_item_id)
);

CREATE INDEX idx_ecom_order_items_product ON ecommerce.order_items(product_id);

-- Order payments
CREATE TABLE ecommerce.order_payments (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES ecommerce.orders(order_id),
    payment_sequential INTEGER NOT NULL,
    payment_type VARCHAR(30) NOT NULL,
    payment_installments INTEGER NOT NULL DEFAULT 1,
    payment_value NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_ecom_order_payments_order ON ecommerce.order_payments(order_id);

-- Order reviews
CREATE TABLE ecommerce.order_reviews (
    review_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES ecommerce.orders(order_id),
    review_score INTEGER NOT NULL CHECK (review_score BETWEEN 1 AND 5),
    review_comment_title TEXT,
    review_comment_message TEXT,
    review_creation_date TIMESTAMP,
    review_answer_timestamp TIMESTAMP
);

CREATE INDEX idx_ecom_order_reviews_order ON ecommerce.order_reviews(order_id);
CREATE INDEX idx_ecom_order_reviews_score ON ecommerce.order_reviews(review_score);

-- Refunds (synthetic — populated during seeding)
CREATE TABLE ecommerce.refunds (
    refund_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES ecommerce.orders(order_id),
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ecom_refunds_order ON ecommerce.refunds(order_id);
CREATE INDEX idx_ecom_refunds_status ON ecommerce.refunds(status);
```

- [ ] **Step 2: Verify migration applies**

Run: `./gradlew :api:bootRun` (briefly, then Ctrl+C after Flyway runs)
Or: connect to the database and verify `ecommerce` schema exists with all 9 tables.

- [ ] **Step 3: Commit**

```bash
git add ecommerce/src/main/resources/db/migration/
git commit -m "feat: add Flyway V6 migration for ecommerce schema"
```

---

## Task 3: JPA Entities

**Files:**
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomProductCategory.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomCustomer.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomSeller.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomProduct.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomOrder.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomOrderItemId.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomOrderItem.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomOrderPayment.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomOrderReview.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/entity/EcomRefund.java`

- [ ] **Step 1: Create EcomProductCategory**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_categories", schema = "ecommerce")
public class EcomProductCategory {

    @Id
    @Column(name = "category_name_pt", length = 100)
    private String categoryNamePt;

    @Column(name = "category_name_en", nullable = false, length = 100)
    private String categoryNameEn;

    protected EcomProductCategory() {}

    public EcomProductCategory(String categoryNamePt, String categoryNameEn) {
        this.categoryNamePt = categoryNamePt;
        this.categoryNameEn = categoryNameEn;
    }

    public String getCategoryNamePt() { return categoryNamePt; }
    public String getCategoryNameEn() { return categoryNameEn; }
}
```

- [ ] **Step 2: Create EcomCustomer**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customers", schema = "ecommerce")
public class EcomCustomer {

    @Id
    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "customer_unique_id", nullable = false, length = 64)
    private String customerUniqueId;

    @Column(name = "zip_code_prefix", length = 10)
    private String zipCodePrefix;

    @Column(length = 255)
    private String city;

    @Column(length = 5)
    private String state;

    protected EcomCustomer() {}

    public EcomCustomer(String customerId, String customerUniqueId, String zipCodePrefix, String city, String state) {
        this.customerId = customerId;
        this.customerUniqueId = customerUniqueId;
        this.zipCodePrefix = zipCodePrefix;
        this.city = city;
        this.state = state;
    }

    public String getCustomerId() { return customerId; }
    public String getCustomerUniqueId() { return customerUniqueId; }
    public String getZipCodePrefix() { return zipCodePrefix; }
    public String getCity() { return city; }
    public String getState() { return state; }
}
```

- [ ] **Step 3: Create EcomSeller**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sellers", schema = "ecommerce")
public class EcomSeller {

    @Id
    @Column(name = "seller_id", length = 64)
    private String sellerId;

    @Column(name = "zip_code_prefix", length = 10)
    private String zipCodePrefix;

    @Column(length = 255)
    private String city;

    @Column(length = 5)
    private String state;

    protected EcomSeller() {}

    public EcomSeller(String sellerId, String zipCodePrefix, String city, String state) {
        this.sellerId = sellerId;
        this.zipCodePrefix = zipCodePrefix;
        this.city = city;
        this.state = state;
    }

    public String getSellerId() { return sellerId; }
    public String getZipCodePrefix() { return zipCodePrefix; }
    public String getCity() { return city; }
    public String getState() { return state; }
}
```

- [ ] **Step 4: Create EcomProduct**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "products", schema = "ecommerce")
public class EcomProduct {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_name_pt", referencedColumnName = "category_name_pt")
    private EcomProductCategory category;

    @Column(name = "name_length")
    private Integer nameLength;

    @Column(name = "description_length")
    private Integer descriptionLength;

    @Column(name = "photos_qty")
    private Integer photosQty;

    @Column(name = "weight_g")
    private Integer weightG;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    protected EcomProduct() {}

    public EcomProduct(String productId, EcomProductCategory category, Integer nameLength,
                       Integer descriptionLength, Integer photosQty, Integer weightG,
                       Integer lengthCm, Integer heightCm, Integer widthCm) {
        this.productId = productId;
        this.category = category;
        this.nameLength = nameLength;
        this.descriptionLength = descriptionLength;
        this.photosQty = photosQty;
        this.weightG = weightG;
        this.lengthCm = lengthCm;
        this.heightCm = heightCm;
        this.widthCm = widthCm;
    }

    public String getProductId() { return productId; }
    public EcomProductCategory getCategory() { return category; }
    public String getCategoryNameEn() {
        return category != null ? category.getCategoryNameEn() : "uncategorized";
    }
    public Integer getNameLength() { return nameLength; }
    public Integer getDescriptionLength() { return descriptionLength; }
    public Integer getPhotosQty() { return photosQty; }
    public Integer getWeightG() { return weightG; }
    public Integer getLengthCm() { return lengthCm; }
    public Integer getHeightCm() { return heightCm; }
    public Integer getWidthCm() { return widthCm; }
}
```

- [ ] **Step 5: Create EcomOrder**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", schema = "ecommerce")
public class EcomOrder {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private EcomCustomer customer;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "purchase_timestamp")
    private LocalDateTime purchaseTimestamp;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "delivered_carrier_date")
    private LocalDateTime deliveredCarrierDate;

    @Column(name = "delivered_customer_date")
    private LocalDateTime deliveredCustomerDate;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<EcomOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<EcomOrderPayment> payments = new ArrayList<>();

    protected EcomOrder() {}

    public String getOrderId() { return orderId; }
    public EcomCustomer getCustomer() { return customer; }
    public String getStatus() { return status; }
    public LocalDateTime getPurchaseTimestamp() { return purchaseTimestamp; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getDeliveredCarrierDate() { return deliveredCarrierDate; }
    public LocalDateTime getDeliveredCustomerDate() { return deliveredCustomerDate; }
    public LocalDateTime getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public List<EcomOrderItem> getItems() { return items; }
    public List<EcomOrderPayment> getPayments() { return payments; }

    public BigDecimal getTotal() {
        return items.stream()
            .map(i -> i.getPrice().add(i.getFreightValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

- [ ] **Step 6: Create EcomOrderItemId (composite key)**

```java
package com.example.ecommerce.entity;

import java.io.Serializable;
import java.util.Objects;

public class EcomOrderItemId implements Serializable {

    private String order;
    private Integer orderItemId;

    public EcomOrderItemId() {}

    public EcomOrderItemId(String order, Integer orderItemId) {
        this.order = order;
        this.orderItemId = orderItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EcomOrderItemId that)) return false;
        return Objects.equals(order, that.order) && Objects.equals(orderItemId, that.orderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, orderItemId);
    }
}
```

- [ ] **Step 7: Create EcomOrderItem**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items", schema = "ecommerce")
@IdClass(EcomOrderItemId.class)
public class EcomOrderItem {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private EcomOrder order;

    @Id
    @Column(name = "order_item_id")
    private Integer orderItemId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private EcomProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private EcomSeller seller;

    @Column(name = "shipping_limit_date")
    private LocalDateTime shippingLimitDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "freight_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal freightValue;

    protected EcomOrderItem() {}

    public EcomOrder getOrder() { return order; }
    public Integer getOrderItemId() { return orderItemId; }
    public EcomProduct getProduct() { return product; }
    public EcomSeller getSeller() { return seller; }
    public LocalDateTime getShippingLimitDate() { return shippingLimitDate; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFreightValue() { return freightValue; }
}
```

- [ ] **Step 8: Create EcomOrderPayment**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_payments", schema = "ecommerce")
public class EcomOrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private EcomOrder order;

    @Column(name = "payment_sequential", nullable = false)
    private Integer paymentSequential;

    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "payment_installments", nullable = false)
    private Integer paymentInstallments;

    @Column(name = "payment_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal paymentValue;

    protected EcomOrderPayment() {}

    public Integer getId() { return id; }
    public EcomOrder getOrder() { return order; }
    public Integer getPaymentSequential() { return paymentSequential; }
    public String getPaymentType() { return paymentType; }
    public Integer getPaymentInstallments() { return paymentInstallments; }
    public BigDecimal getPaymentValue() { return paymentValue; }
}
```

- [ ] **Step 9: Create EcomOrderReview**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_reviews", schema = "ecommerce")
public class EcomOrderReview {

    @Id
    @Column(name = "review_id", length = 64)
    private String reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private EcomOrder order;

    @Column(name = "review_score", nullable = false)
    private Integer reviewScore;

    @Column(name = "review_comment_title", columnDefinition = "TEXT")
    private String reviewCommentTitle;

    @Column(name = "review_comment_message", columnDefinition = "TEXT")
    private String reviewCommentMessage;

    @Column(name = "review_creation_date")
    private LocalDateTime reviewCreationDate;

    @Column(name = "review_answer_timestamp")
    private LocalDateTime reviewAnswerTimestamp;

    protected EcomOrderReview() {}

    public String getReviewId() { return reviewId; }
    public EcomOrder getOrder() { return order; }
    public Integer getReviewScore() { return reviewScore; }
    public String getReviewCommentTitle() { return reviewCommentTitle; }
    public String getReviewCommentMessage() { return reviewCommentMessage; }
    public LocalDateTime getReviewCreationDate() { return reviewCreationDate; }
    public LocalDateTime getReviewAnswerTimestamp() { return reviewAnswerTimestamp; }
}
```

- [ ] **Step 10: Create EcomRefund**

```java
package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds", schema = "ecommerce")
public class EcomRefund {

    @Id
    @Column(name = "refund_id", length = 64)
    private String refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private EcomOrder order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected EcomRefund() {}

    public EcomRefund(String refundId, EcomOrder order, BigDecimal amount, String status, String reason, LocalDateTime createdAt) {
        this.refundId = refundId;
        this.order = order;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getRefundId() { return refundId; }
    public EcomOrder getOrder() { return order; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 11: Verify build compiles**

Run: `./gradlew :ecommerce:build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/entity/
git commit -m "feat: add JPA entities for ecommerce schema (Olist dataset)"
```

---

## Task 4: Repositories

**Files:**
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomProductCategoryRepository.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomCustomerRepository.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomProductRepository.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomOrderRepository.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomOrderItemRepository.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomOrderReviewRepository.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/repository/EcomRefundRepository.java`

- [ ] **Step 1: Create EcomProductCategoryRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomProductCategoryRepository extends JpaRepository<EcomProductCategory, String> {

    @Query("""
        SELECT pc.categoryNameEn, COUNT(p) FROM EcomProduct p
        JOIN p.category pc
        GROUP BY pc.categoryNameEn
        ORDER BY pc.categoryNameEn
        """)
    List<Object[]> findAllWithProductCounts();
}
```

- [ ] **Step 2: Create EcomCustomerRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomCustomer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomCustomerRepository extends JpaRepository<EcomCustomer, String> {

    List<EcomCustomer> findByCityContainingIgnoreCaseOrStateContainingIgnoreCase(
            String city, String state, Pageable pageable);

    @Query("SELECT COUNT(o) FROM EcomOrder o WHERE o.customer.customerId = :customerId")
    long countOrdersByCustomerId(String customerId);
}
```

- [ ] **Step 3: Create EcomProductRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomProductRepository extends JpaRepository<EcomProduct, String> {

    @Query("""
        SELECT p FROM EcomProduct p
        JOIN p.category pc
        WHERE LOWER(pc.categoryNameEn) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    List<EcomProduct> searchByCategory(String query, Pageable pageable);

    @Query("""
        SELECT p FROM EcomProduct p
        JOIN p.category pc
        WHERE LOWER(pc.categoryNameEn) = LOWER(:category)
        """)
    List<EcomProduct> findByCategory(String category, Pageable pageable);

    @Query("""
        SELECT p FROM EcomProduct p
        JOIN p.category pc
        WHERE LOWER(pc.categoryNameEn) LIKE LOWER(CONCAT('%', :query, '%'))
        AND LOWER(pc.categoryNameEn) = LOWER(:category)
        """)
    List<EcomProduct> searchByCategoryFiltered(String query, String category, Pageable pageable);
}
```

- [ ] **Step 4: Create EcomOrderRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomOrderRepository extends JpaRepository<EcomOrder, String> {

    @Query("""
        SELECT o FROM EcomOrder o
        JOIN FETCH o.customer
        WHERE o.customer.customerId = :customerId
        ORDER BY o.purchaseTimestamp DESC
        """)
    List<EcomOrder> findByCustomerIdOrderByPurchaseDesc(String customerId, Pageable pageable);

    @Query("""
        SELECT o FROM EcomOrder o
        JOIN FETCH o.customer
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        LEFT JOIN FETCH o.payments
        WHERE o.orderId = :orderId
        """)
    java.util.Optional<EcomOrder> findByIdWithDetails(String orderId);
}
```

- [ ] **Step 5: Create EcomOrderItemRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomOrderItem;
import com.example.ecommerce.entity.EcomOrderItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EcomOrderItemRepository extends JpaRepository<EcomOrderItem, EcomOrderItemId> {

    List<EcomOrderItem> findByProductProductId(String productId);
}
```

- [ ] **Step 6: Create EcomOrderReviewRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomOrderReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EcomOrderReviewRepository extends JpaRepository<EcomOrderReview, String> {

    Optional<EcomOrderReview> findByOrderOrderId(String orderId);

    @Query("""
        SELECT r FROM EcomOrderReview r
        JOIN EcomOrderItem oi ON r.order.orderId = oi.order.orderId
        WHERE oi.product.productId = :productId
        ORDER BY r.reviewCreationDate DESC
        """)
    List<EcomOrderReview> findByProductId(String productId, Pageable pageable);

    @Query("SELECT AVG(r.reviewScore) FROM EcomOrderReview r JOIN EcomOrderItem oi ON r.order.orderId = oi.order.orderId WHERE oi.product.productId = :productId")
    Double findAverageScoreByProductId(String productId);

    @Query("SELECT COUNT(r) FROM EcomOrderReview r JOIN EcomOrderItem oi ON r.order.orderId = oi.order.orderId WHERE oi.product.productId = :productId")
    Long countByProductId(String productId);
}
```

- [ ] **Step 7: Create EcomRefundRepository**

```java
package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EcomRefundRepository extends JpaRepository<EcomRefund, String> {

    Optional<EcomRefund> findByOrderOrderId(String orderId);
}
```

- [ ] **Step 8: Verify build compiles**

Run: `./gradlew :ecommerce:build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/repository/
git commit -m "feat: add Spring Data repositories for ecommerce entities"
```

---

## Task 5: Exceptions and Service Classes

**Files:**
- Create: `ecommerce/src/main/java/com/example/ecommerce/exception/OrderNotFoundException.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/exception/CustomerNotFoundException.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/exception/ProductNotFoundException.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/exception/RefundNotEligibleException.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/exception/OrderNotCancellableException.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/service/EcommerceProductService.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/service/EcommerceOrderService.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/service/EcommerceCustomerService.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/service/EcommerceRefundService.java`
- Create: `ecommerce/src/main/java/com/example/ecommerce/service/EcommerceReviewService.java`

- [ ] **Step 1: Create exception classes**

```java
// OrderNotFoundException.java
package com.example.ecommerce.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}

// CustomerNotFoundException.java
package com.example.ecommerce.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String customerId) {
        super("Customer not found: " + customerId);
    }
}

// ProductNotFoundException.java
package com.example.ecommerce.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}

// RefundNotEligibleException.java
package com.example.ecommerce.exception;

public class RefundNotEligibleException extends RuntimeException {
    public RefundNotEligibleException(String reason) {
        super(reason);
    }
}

// OrderNotCancellableException.java
package com.example.ecommerce.exception;

public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(String reason) {
        super(reason);
    }
}
```

- [ ] **Step 2: Create EcommerceProductService**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomProduct;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.EcomOrderReviewRepository;
import com.example.ecommerce.repository.EcomProductCategoryRepository;
import com.example.ecommerce.repository.EcomProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EcommerceProductService {

    private final EcomProductRepository productRepo;
    private final EcomProductCategoryRepository categoryRepo;
    private final EcomOrderReviewRepository reviewRepo;

    public EcommerceProductService(EcomProductRepository productRepo,
                                   EcomProductCategoryRepository categoryRepo,
                                   EcomOrderReviewRepository reviewRepo) {
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
        this.reviewRepo = reviewRepo;
    }

    public EcomProduct getProduct(String productId) {
        return productRepo.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public List<EcomProduct> searchProducts(String query, String category, int limit) {
        var pageable = PageRequest.of(0, limit);
        if (category != null && !category.isBlank()) {
            return productRepo.searchByCategoryFiltered(query, category, pageable);
        }
        return productRepo.searchByCategory(query, pageable);
    }

    public List<Object[]> listCategories() {
        return categoryRepo.findAllWithProductCounts();
    }

    public Double getAverageRating(String productId) {
        return reviewRepo.findAverageScoreByProductId(productId);
    }

    public Long getReviewCount(String productId) {
        return reviewRepo.countByProductId(productId);
    }
}
```

- [ ] **Step 3: Create EcommerceOrderService**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.repository.EcomOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EcommerceOrderService {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of("created", "approved", "processing", "invoiced");

    private final EcomOrderRepository orderRepo;

    public EcommerceOrderService(EcomOrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    public EcomOrder getOrder(String orderId) {
        return orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<EcomOrder> listCustomerOrders(String customerId, int limit) {
        return orderRepo.findByCustomerIdOrderByPurchaseDesc(customerId, PageRequest.of(0, limit));
    }

    @Transactional
    public EcomOrder cancelOrder(String orderId, String reason) {
        var order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new OrderNotCancellableException(
                    "Order " + orderId + " cannot be cancelled (status: " + order.getStatus() + ")");
        }

        // In a real system we'd update status. With Olist read-only data, we simulate success.
        return order;
    }
}
```

- [ ] **Step 4: Create EcommerceCustomerService**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomCustomer;
import com.example.ecommerce.exception.CustomerNotFoundException;
import com.example.ecommerce.repository.EcomCustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EcommerceCustomerService {

    private final EcomCustomerRepository customerRepo;

    public EcommerceCustomerService(EcomCustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    public EcomCustomer getCustomer(String customerId) {
        return customerRepo.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    public List<EcomCustomer> searchCustomers(String query, int limit) {
        return customerRepo.findByCityContainingIgnoreCaseOrStateContainingIgnoreCase(
                query, query, PageRequest.of(0, limit));
    }

    public long getOrderCount(String customerId) {
        return customerRepo.countOrdersByCustomerId(customerId);
    }
}
```

- [ ] **Step 5: Create EcommerceRefundService**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.entity.EcomRefund;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.exception.RefundNotEligibleException;
import com.example.ecommerce.repository.EcomOrderRepository;
import com.example.ecommerce.repository.EcomRefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EcommerceRefundService {

    private final EcomOrderRepository orderRepo;
    private final EcomRefundRepository refundRepo;

    public EcommerceRefundService(EcomOrderRepository orderRepo, EcomRefundRepository refundRepo) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
    }

    public record EligibilityResult(boolean eligible, String reason, BigDecimal eligibleAmount, String orderStatus) {}

    public EligibilityResult checkEligibility(String orderId) {
        var order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if ("delivered".equals(order.getStatus())) {
            return new EligibilityResult(true, "Order delivered — eligible for refund",
                    order.getTotal(), order.getStatus());
        }

        return new EligibilityResult(false,
                "Order not eligible (status: " + order.getStatus() + "). Refunds require delivered status.",
                BigDecimal.ZERO, order.getStatus());
    }

    @Transactional
    public EcomRefund initiateRefund(String orderId, BigDecimal amount, String reason) {
        var order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!"delivered".equals(order.getStatus())) {
            throw new RefundNotEligibleException(
                    "Order " + orderId + " is not eligible for refund (status: " + order.getStatus() + ")");
        }

        var existing = refundRepo.findByOrderOrderId(orderId);
        if (existing.isPresent()) {
            throw new RefundNotEligibleException(
                    "Refund already exists for order " + orderId + " (refund: " + existing.get().getRefundId() + ")");
        }

        var refund = new EcomRefund(
                "REF-" + UUID.randomUUID().toString().substring(0, 8),
                order, amount, "PROCESSING", reason, LocalDateTime.now());
        return refundRepo.save(refund);
    }

    public Optional<EcomRefund> getRefundStatus(String orderId) {
        return refundRepo.findByOrderOrderId(orderId);
    }
}
```

- [ ] **Step 6: Create EcommerceReviewService**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrderReview;
import com.example.ecommerce.repository.EcomOrderReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EcommerceReviewService {

    private final EcomOrderReviewRepository reviewRepo;

    public EcommerceReviewService(EcomOrderReviewRepository reviewRepo) {
        this.reviewRepo = reviewRepo;
    }

    public Optional<EcomOrderReview> getOrderReview(String orderId) {
        return reviewRepo.findByOrderOrderId(orderId);
    }

    public List<EcomOrderReview> getProductReviews(String productId, int limit) {
        return reviewRepo.findByProductId(productId, PageRequest.of(0, limit));
    }
}
```

- [ ] **Step 7: Verify build compiles**

Run: `./gradlew :ecommerce:build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/exception/ ecommerce/src/main/java/com/example/ecommerce/service/
git commit -m "feat: add ecommerce service layer and domain exceptions"
```

---

## Task 6: Olist Data Seeder

**Files:**
- Create: `ecommerce/src/main/java/com/example/ecommerce/seed/OlistDataSeeder.java`

This uses JDBC batch inserts for performance. Enabled only with `seed` profile.

- [ ] **Step 1: Create OlistDataSeeder**

```java
package com.example.ecommerce.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.*;

@Component
@Profile("seed")
public class OlistDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OlistDataSeeder.class);
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;

    public OlistDataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isAlreadySeeded()) {
            log.info("Ecommerce data already seeded — skipping");
            return;
        }

        log.info("Starting Olist data seeding...");
        long start = System.currentTimeMillis();

        seedCategories();
        seedCustomers();
        seedSellers();
        seedProducts();
        seedOrders();
        seedOrderItems();
        seedOrderPayments();
        seedOrderReviews();
        synthesizeRefunds();

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("Olist data seeding completed in {}s", elapsed);
    }

    private boolean isAlreadySeeded() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM ecommerce.orders", Long.class);
        return count != null && count > 0;
    }

    private void seedCategories() {
        log.info("Seeding product categories...");
        var rows = readCsv("seed/product_category_name_translation.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.product_categories (category_name_pt, category_name_en) VALUES (?, ?) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("product_category_name"));
                    ps.setString(2, row.get("product_category_name_english"));
                });
        log.info("Seeded {} categories", rows.size());
    }

    private void seedCustomers() {
        log.info("Seeding customers...");
        var rows = readCsv("seed/olist_customers_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.customers (customer_id, customer_unique_id, zip_code_prefix, city, state) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("customer_id"));
                    ps.setString(2, row.get("customer_unique_id"));
                    ps.setString(3, row.get("customer_zip_code_prefix"));
                    ps.setString(4, row.get("customer_city"));
                    ps.setString(5, row.get("customer_state"));
                });
        log.info("Seeded {} customers", rows.size());
    }

    private void seedSellers() {
        log.info("Seeding sellers...");
        var rows = readCsv("seed/olist_sellers_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.sellers (seller_id, zip_code_prefix, city, state) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("seller_id"));
                    ps.setString(2, row.get("seller_zip_code_prefix"));
                    ps.setString(3, row.get("seller_city"));
                    ps.setString(4, row.get("seller_state"));
                });
        log.info("Seeded {} sellers", rows.size());
    }

    private void seedProducts() {
        log.info("Seeding products...");
        var rows = readCsv("seed/olist_products_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.products (product_id, category_name_pt, name_length, description_length, photos_qty, weight_g, length_cm, height_cm, width_cm) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("product_id"));
                    setStringOrNull(ps, 2, row.get("product_category_name"));
                    setIntOrNull(ps, 3, row.get("product_name_lenght"));
                    setIntOrNull(ps, 4, row.get("product_description_lenght"));
                    setIntOrNull(ps, 5, row.get("product_photos_qty"));
                    setIntOrNull(ps, 6, row.get("product_weight_g"));
                    setIntOrNull(ps, 7, row.get("product_length_cm"));
                    setIntOrNull(ps, 8, row.get("product_height_cm"));
                    setIntOrNull(ps, 9, row.get("product_width_cm"));
                });
        log.info("Seeded {} products", rows.size());
    }

    private void seedOrders() {
        log.info("Seeding orders...");
        var rows = readCsv("seed/olist_orders_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.orders (order_id, customer_id, status, purchase_timestamp, approved_at, delivered_carrier_date, delivered_customer_date, estimated_delivery_date) VALUES (?, ?, ?, ?::timestamp, ?::timestamp, ?::timestamp, ?::timestamp, ?::timestamp) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("order_id"));
                    ps.setString(2, row.get("customer_id"));
                    ps.setString(3, row.get("order_status"));
                    setStringOrNull(ps, 4, row.get("order_purchase_timestamp"));
                    setStringOrNull(ps, 5, row.get("order_approved_at"));
                    setStringOrNull(ps, 6, row.get("order_delivered_carrier_date"));
                    setStringOrNull(ps, 7, row.get("order_delivered_customer_date"));
                    setStringOrNull(ps, 8, row.get("order_estimated_delivery_date"));
                });
        log.info("Seeded {} orders", rows.size());
    }

    private void seedOrderItems() {
        log.info("Seeding order items...");
        var rows = readCsv("seed/olist_order_items_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.order_items (order_id, order_item_id, product_id, seller_id, shipping_limit_date, price, freight_value) VALUES (?, ?, ?, ?, ?::timestamp, ?::numeric, ?::numeric) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("order_id"));
                    ps.setInt(2, Integer.parseInt(row.get("order_item_id")));
                    ps.setString(3, row.get("product_id"));
                    ps.setString(4, row.get("seller_id"));
                    setStringOrNull(ps, 5, row.get("shipping_limit_date"));
                    ps.setString(6, row.get("price"));
                    ps.setString(7, row.get("freight_value"));
                });
        log.info("Seeded {} order items", rows.size());
    }

    private void seedOrderPayments() {
        log.info("Seeding order payments...");
        var rows = readCsv("seed/olist_order_payments_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.order_payments (order_id, payment_sequential, payment_type, payment_installments, payment_value) VALUES (?, ?, ?, ?, ?::numeric)",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("order_id"));
                    ps.setInt(2, Integer.parseInt(row.get("payment_sequential")));
                    ps.setString(3, row.get("payment_type"));
                    ps.setInt(4, Integer.parseInt(row.get("payment_installments")));
                    ps.setString(5, row.get("payment_value"));
                });
        log.info("Seeded {} order payments", rows.size());
    }

    private void seedOrderReviews() {
        log.info("Seeding order reviews...");
        var rows = readCsv("seed/olist_order_reviews_dataset.csv");
        jdbc.batchUpdate(
                "INSERT INTO ecommerce.order_reviews (review_id, order_id, review_score, review_comment_title, review_comment_message, review_creation_date, review_answer_timestamp) VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp) ON CONFLICT DO NOTHING",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setString(1, row.get("review_id"));
                    ps.setString(2, row.get("order_id"));
                    ps.setInt(3, Integer.parseInt(row.get("review_score")));
                    setStringOrNull(ps, 4, row.get("review_comment_title"));
                    setStringOrNull(ps, 5, row.get("review_comment_message"));
                    setStringOrNull(ps, 6, row.get("review_creation_date"));
                    setStringOrNull(ps, 7, row.get("review_answer_timestamp"));
                });
        log.info("Seeded {} order reviews", rows.size());
    }

    private void synthesizeRefunds() {
        log.info("Synthesizing refunds...");
        var random = new Random(42);

        // Canceled orders -> COMPLETED refunds
        int canceledRefunds = jdbc.update("""
            INSERT INTO ecommerce.refunds (refund_id, order_id, amount, status, reason, created_at)
            SELECT
                'REF-' || LEFT(o.order_id, 8),
                o.order_id,
                COALESCE((SELECT SUM(oi.price + oi.freight_value) FROM ecommerce.order_items oi WHERE oi.order_id = o.order_id), 0),
                'COMPLETED',
                'Order was canceled',
                COALESCE(o.purchase_timestamp, NOW())
            FROM ecommerce.orders o
            WHERE o.status = 'canceled'
            ON CONFLICT DO NOTHING
            """);
        log.info("Synthesized {} refunds from canceled orders", canceledRefunds);

        // Low-review delivered orders -> PROCESSING/PENDING refunds
        int lowReviewRefunds = jdbc.update("""
            INSERT INTO ecommerce.refunds (refund_id, order_id, amount, status, reason, created_at)
            SELECT
                'REF-' || LEFT(r.review_id, 8),
                o.order_id,
                COALESCE((SELECT SUM(oi.price + oi.freight_value) FROM ecommerce.order_items oi WHERE oi.order_id = o.order_id), 0),
                CASE WHEN r.review_score = 1 THEN 'PROCESSING' ELSE 'PENDING' END,
                COALESCE(r.review_comment_message, 'Customer dissatisfied'),
                COALESCE(r.review_creation_date, NOW())
            FROM ecommerce.order_reviews r
            JOIN ecommerce.orders o ON r.order_id = o.order_id
            WHERE r.review_score <= 2 AND o.status = 'delivered'
            AND NOT EXISTS (SELECT 1 FROM ecommerce.refunds ref WHERE ref.order_id = o.order_id)
            ON CONFLICT DO NOTHING
            """);
        log.info("Synthesized {} refunds from low-review orders", lowReviewRefunds);
    }

    private List<Map<String, String>> readCsv(String resourcePath) {
        var result = new ArrayList<Map<String, String>>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return result;

            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                var row = new HashMap<String, String>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String val = values[i].trim();
                    row.put(headers[i].trim(), val.isEmpty() ? null : val);
                }
                result.add(row);
            }
        } catch (Exception e) {
            log.error("Failed to read CSV: {}", resourcePath, e);
        }
        return result;
    }

    private String[] parseCsvLine(String line) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private void setStringOrNull(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private void setIntOrNull(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, Integer.parseInt(value.trim()));
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :ecommerce:build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add ecommerce/src/main/java/com/example/ecommerce/seed/
git commit -m "feat: add Olist data seeder with JDBC batch inserts and refund synthesis"
```

---

## Task 7: Agent Tools Refactoring (Profile-Based Switching)

**Files:**
- Create: `agents/src/main/java/com/example/agents/tools/OrderDataProvider.java`
- Create: `agents/src/main/java/com/example/agents/tools/RefundDataProvider.java`
- Create: `agents/src/main/java/com/example/agents/tools/olist/OlistOrderDataProvider.java`
- Create: `agents/src/main/java/com/example/agents/tools/olist/OlistRefundDataProvider.java`
- Create: `agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonOrderDataProvider.java`
- Create: `agents/src/main/java/com/example/agents/tools/dummyjson/DummyJsonRefundDataProvider.java`
- Modify: `agents/src/main/java/com/example/agents/tools/OrderTools.java`
- Modify: `agents/src/main/java/com/example/agents/tools/RefundTools.java`

The strategy: introduce `OrderDataProvider` and `RefundDataProvider` interfaces. Two implementations each — Olist (default) and DummyJSON (fallback). Tools depend on the interface. Profile-based `@ConditionalOnProperty` selects the impl.

- [ ] **Step 1: Create OrderDataProvider interface**

```java
package com.example.agents.tools;

public interface OrderDataProvider {

    record OrderDetails(String orderId, String status, String items, String total,
                        String customerId, String purchaseDate, String deliveryDate,
                        String estimatedDeliveryDate, String paymentInfo) {}

    record ShipmentDetails(String orderId, String status, String estimatedDelivery,
                           String actualDelivery, String carrierDate, String freightInfo) {}

    record OrderSummary(String orderId, String total, String status, String items) {}

    OrderDetails getOrder(String orderId);
    ShipmentDetails trackShipment(String orderId);
    String cancelOrder(String orderId, String reason);
    java.util.List<OrderSummary> getRecentOrders(String customerId);
}
```

- [ ] **Step 2: Create RefundDataProvider interface**

```java
package com.example.agents.tools;

public interface RefundDataProvider {

    record RefundPolicyResult(String orderId, String status, String items,
                              boolean eligible, String eligibleAmount, String reason,
                              String returnPolicy) {}

    record RefundResult(String orderId, String refundId, String amount,
                        String status, String reason) {}

    record RefundStatusResult(String orderId, String refundId, String amount,
                              String status, String completionInfo) {}

    RefundPolicyResult checkRefundPolicy(String orderId);
    RefundResult initiateRefund(String orderId, String amount, String reason);
    RefundStatusResult checkRefundStatus(String orderId);
}
```

- [ ] **Step 3: Create OlistOrderDataProvider**

```java
package com.example.agents.tools.olist;

import com.example.agents.tools.OrderDataProvider;
import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.service.EcommerceOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "olist", matchIfMissing = true)
public class OlistOrderDataProvider implements OrderDataProvider {

    private final EcommerceOrderService orderService;

    public OlistOrderDataProvider(EcommerceOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderDetails getOrder(String orderId) {
        var order = orderService.getOrder(orderId);
        String items = order.getItems().stream()
                .map(i -> i.getProduct().getCategoryNameEn() + " ($" + i.getPrice() + ")")
                .collect(Collectors.joining(", "));
        String paymentInfo = order.getPayments().stream()
                .map(p -> p.getPaymentType() + " $" + p.getPaymentValue())
                .collect(Collectors.joining(", "));
        return new OrderDetails(
                order.getOrderId(), order.getStatus(), items,
                "$" + order.getTotal().toPlainString(),
                order.getCustomer().getCustomerId(),
                order.getPurchaseTimestamp() != null ? order.getPurchaseTimestamp().toString() : "N/A",
                order.getDeliveredCustomerDate() != null ? order.getDeliveredCustomerDate().toString() : "Not yet delivered",
                order.getEstimatedDeliveryDate() != null ? order.getEstimatedDeliveryDate().toString() : "N/A",
                paymentInfo);
    }

    @Override
    public ShipmentDetails trackShipment(String orderId) {
        var order = orderService.getOrder(orderId);
        String freightInfo = order.getItems().stream()
                .map(i -> "freight: $" + i.getFreightValue())
                .collect(Collectors.joining(", "));
        return new ShipmentDetails(
                order.getOrderId(), order.getStatus(),
                order.getEstimatedDeliveryDate() != null ? order.getEstimatedDeliveryDate().toString() : "N/A",
                order.getDeliveredCustomerDate() != null ? order.getDeliveredCustomerDate().toString() : "Not yet delivered",
                order.getDeliveredCarrierDate() != null ? order.getDeliveredCarrierDate().toString() : "Not yet handed to carrier",
                freightInfo);
    }

    @Override
    public String cancelOrder(String orderId, String reason) {
        try {
            var order = orderService.cancelOrder(orderId, reason);
            return "Order " + orderId + " has been successfully cancelled. Reason: " + reason
                    + ". A full refund of " + order.getTotal().toPlainString()
                    + " will be processed within 5-7 business days.";
        } catch (OrderNotCancellableException e) {
            return e.getMessage();
        } catch (OrderNotFoundException e) {
            return "ORDER_NOT_FOUND: " + e.getMessage();
        }
    }

    @Override
    public List<OrderSummary> getRecentOrders(String customerId) {
        return orderService.listCustomerOrders(customerId, 10).stream()
                .map(o -> new OrderSummary(o.getOrderId(),
                        "$" + o.getTotal().toPlainString(),
                        o.getStatus(), ""))
                .toList();
    }
}
```

- [ ] **Step 4: Create OlistRefundDataProvider**

```java
package com.example.agents.tools.olist;

import com.example.agents.tools.RefundDataProvider;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.exception.RefundNotEligibleException;
import com.example.ecommerce.service.EcommerceRefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "olist", matchIfMissing = true)
public class OlistRefundDataProvider implements RefundDataProvider {

    private final EcommerceRefundService refundService;

    public OlistRefundDataProvider(EcommerceRefundService refundService) {
        this.refundService = refundService;
    }

    @Override
    public RefundPolicyResult checkRefundPolicy(String orderId) {
        var result = refundService.checkEligibility(orderId);
        return new RefundPolicyResult(orderId, result.orderStatus(), "",
                result.eligible(), "$" + result.eligibleAmount().toPlainString(),
                result.reason(), "30 days return policy");
    }

    @Override
    public RefundResult initiateRefund(String orderId, String amount, String reason) {
        try {
            var refund = refundService.initiateRefund(orderId, new BigDecimal(amount), reason);
            return new RefundResult(orderId, refund.getRefundId(),
                    "$" + refund.getAmount().toPlainString(), refund.getStatus(), reason);
        } catch (OrderNotFoundException e) {
            return new RefundResult(orderId, null, null, "ORDER_NOT_FOUND", e.getMessage());
        } catch (RefundNotEligibleException e) {
            return new RefundResult(orderId, null, null, "NOT_ELIGIBLE", e.getMessage());
        }
    }

    @Override
    public RefundStatusResult checkRefundStatus(String orderId) {
        var refund = refundService.getRefundStatus(orderId);
        if (refund.isEmpty()) {
            return new RefundStatusResult(orderId, null, null, "NO_REFUND",
                    "No refund found for order " + orderId);
        }
        var r = refund.get();
        String completionInfo = "COMPLETED".equals(r.getStatus())
                ? "Refund has been credited to the original payment method."
                : "Refund is being processed. Expected completion: 5-7 business days.";
        return new RefundStatusResult(orderId, r.getRefundId(),
                "$" + r.getAmount().toPlainString(), r.getStatus(), completionInfo);
    }
}
```

- [ ] **Step 5: Create DummyJsonOrderDataProvider**

Wrap the existing `DummyJsonClient` logic from the current `OrderTools`:

```java
package com.example.agents.tools.dummyjson;

import com.example.agents.tools.OrderDataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "dummyjson")
public class DummyJsonOrderDataProvider implements OrderDataProvider {

    private final DummyJsonClient dummyJson;

    public DummyJsonOrderDataProvider(DummyJsonClient dummyJson) {
        this.dummyJson = dummyJson;
    }

    @Override
    public OrderDetails getOrder(String orderId) {
        int id = parseId(orderId);
        if (id < 0) return null;
        var cartOpt = dummyJson.getCart(id);
        if (cartOpt.isEmpty()) return null;
        var cart = cartOpt.get();
        String items = cart.products().stream()
                .map(p -> p.quantity() + "x " + p.title())
                .collect(Collectors.joining(", "));
        return new OrderDetails(String.valueOf(cart.id()), deriveStatus(cart.id()), items,
                "$" + String.format("%.2f", cart.discountedTotal()),
                String.valueOf(cart.userId()), "N/A", "N/A", "N/A", "N/A");
    }

    @Override
    public ShipmentDetails trackShipment(String orderId) {
        int id = parseId(orderId);
        if (id < 0) return null;
        var cartOpt = dummyJson.getCart(id);
        if (cartOpt.isEmpty()) return null;
        var cart = cartOpt.get();
        return new ShipmentDetails(String.valueOf(cart.id()), deriveStatus(cart.id()),
                "N/A", "N/A", "N/A", "N/A");
    }

    @Override
    public String cancelOrder(String orderId, String reason) {
        int id = parseId(orderId);
        if (id < 0) return "INVALID_ORDER_ID";
        var cartOpt = dummyJson.getCart(id);
        if (cartOpt.isEmpty()) return "ORDER_NOT_FOUND";
        String status = deriveStatus(id);
        if ("SHIPPED".equals(status) || "DELIVERED".equals(status)) {
            return "Order cannot be cancelled — already " + status.toLowerCase();
        }
        return "Order " + id + " cancelled. Reason: " + reason;
    }

    @Override
    public List<OrderSummary> getRecentOrders(String customerId) {
        int userId = parseId(customerId);
        if (userId < 1) return List.of();
        return dummyJson.getCartsByUser(userId).stream()
                .map(c -> new OrderSummary(String.valueOf(c.id()),
                        "$" + String.format("%.2f", c.discountedTotal()),
                        deriveStatus(c.id()), ""))
                .toList();
    }

    private int parseId(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private String deriveStatus(int id) {
        if (id <= 10) return "DELIVERED";
        if (id <= 20) return "SHIPPED";
        if (id <= 25) return "PROCESSING";
        return "PENDING";
    }
}
```

- [ ] **Step 6: Create DummyJsonRefundDataProvider**

```java
package com.example.agents.tools.dummyjson;

import com.example.agents.tools.RefundDataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "dummyjson")
public class DummyJsonRefundDataProvider implements RefundDataProvider {

    private final DummyJsonClient dummyJson;

    public DummyJsonRefundDataProvider(DummyJsonClient dummyJson) {
        this.dummyJson = dummyJson;
    }

    @Override
    public RefundPolicyResult checkRefundPolicy(String orderId) {
        int id = parseId(orderId);
        if (id < 0) return new RefundPolicyResult(orderId, "INVALID", "", false, "$0", "Invalid order ID", "");
        var cartOpt = dummyJson.getCart(id);
        if (cartOpt.isEmpty()) return new RefundPolicyResult(orderId, "NOT_FOUND", "", false, "$0", "Order not found", "");
        var cart = cartOpt.get();
        boolean eligible = deriveStatus(id).equals("DELIVERED");
        return new RefundPolicyResult(orderId, deriveStatus(id), "",
                eligible, "$" + String.format("%.2f", cart.discountedTotal()),
                eligible ? "Eligible for refund" : "Not yet delivered", "30 days return policy");
    }

    @Override
    public RefundResult initiateRefund(String orderId, String amount, String reason) {
        int id = parseId(orderId);
        if (id < 0) return new RefundResult(orderId, null, null, "INVALID", "Invalid order ID");
        if (!deriveStatus(id).equals("DELIVERED"))
            return new RefundResult(orderId, null, null, "NOT_ELIGIBLE", "Order not delivered");
        return new RefundResult(orderId, "REF-" + (2000 + id), "$" + amount, "PROCESSING", reason);
    }

    @Override
    public RefundStatusResult checkRefundStatus(String orderId) {
        int id = parseId(orderId);
        if (id < 0) return new RefundStatusResult(orderId, null, null, "INVALID", "Invalid order ID");
        var cartOpt = dummyJson.getCart(id);
        if (cartOpt.isEmpty()) return new RefundStatusResult(orderId, null, null, "NOT_FOUND", "Order not found");
        var cart = cartOpt.get();
        String status = (id <= 5) ? "COMPLETED" : "PROCESSING";
        return new RefundStatusResult(orderId, "REF-" + (2000 + id),
                "$" + String.format("%.2f", cart.discountedTotal()), status,
                "COMPLETED".equals(status) ? "Credited to original payment method" : "Processing");
    }

    private int parseId(String value) {
        try { return Integer.parseInt(value.trim().replaceAll("^(ORD-|REF-)", "")); }
        catch (NumberFormatException e) { return -1; }
    }

    private String deriveStatus(int id) {
        if (id <= 10) return "DELIVERED";
        if (id <= 20) return "SHIPPED";
        if (id <= 25) return "PROCESSING";
        return "PENDING";
    }
}
```

- [ ] **Step 7: Refactor OrderTools to use OrderDataProvider**

Replace the entire `OrderTools.java`:

```java
package com.example.agents.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    private final OrderDataProvider orderData;

    public OrderTools(OrderDataProvider orderData) {
        this.orderData = orderData;
    }

    @Tool(description = "Look up an order by its order ID. Returns order details if found.")
    public String lookupOrder(@ToolParam(description = "The order ID to look up") String orderId) {
        var order = orderData.getOrder(orderId);
        if (order == null) return "ORDER_NOT_FOUND: No order exists with ID '" + orderId + "'.";
        return "Order Details:\n"
                + "- Order ID: " + order.orderId() + "\n"
                + "- Status: " + order.status() + "\n"
                + "- Items: " + order.items() + "\n"
                + "- Total: " + order.total() + "\n"
                + "- Customer ID: " + order.customerId() + "\n"
                + "- Purchase Date: " + order.purchaseDate() + "\n"
                + "- Delivery Date: " + order.deliveryDate() + "\n"
                + "- Payment: " + order.paymentInfo();
    }

    @Tool(description = "Track the shipment for an order. Returns shipping status.")
    public String trackShipment(@ToolParam(description = "The order ID to track") String orderId) {
        var shipment = orderData.trackShipment(orderId);
        if (shipment == null) return "ORDER_NOT_FOUND: No order exists with ID '" + orderId + "'.";
        if ("created".equals(shipment.status()) || "approved".equals(shipment.status()) || "processing".equals(shipment.status())) {
            return "NO_SHIPMENT: Order " + shipment.orderId() + " has not been shipped yet. Status: " + shipment.status();
        }
        return "Shipment Tracking:\n"
                + "- Order: " + shipment.orderId() + "\n"
                + "- Status: " + shipment.status() + "\n"
                + "- Estimated Delivery: " + shipment.estimatedDelivery() + "\n"
                + "- Actual Delivery: " + shipment.actualDelivery() + "\n"
                + "- Handed to Carrier: " + shipment.carrierDate() + "\n"
                + "- Freight: " + shipment.freightInfo();
    }

    @Tool(description = "Cancel an order. Only works for orders that haven't been shipped yet.")
    public String cancelOrder(
            @ToolParam(description = "The order ID to cancel") String orderId,
            @ToolParam(description = "Reason for cancellation") String reason) {
        return orderData.cancelOrder(orderId, reason);
    }

    @Tool(description = "Get the list of recent orders for a customer.")
    public String getRecentOrders(@ToolParam(description = "The customer/user ID") String customerId) {
        var orders = orderData.getRecentOrders(customerId);
        if (orders.isEmpty()) return "No orders found for customer " + customerId + ".";
        var sb = new StringBuilder("Recent orders for customer " + customerId + ":\n");
        for (var o : orders) {
            sb.append("- Order #").append(o.orderId())
                    .append(" | ").append(o.total())
                    .append(" | ").append(o.status())
                    .append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 8: Refactor RefundTools to use RefundDataProvider**

Replace the entire `RefundTools.java`:

```java
package com.example.agents.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RefundTools {

    private final RefundDataProvider refundData;

    public RefundTools(RefundDataProvider refundData) {
        this.refundData = refundData;
    }

    @Tool(description = "Check if an order is eligible for a refund based on the return policy.")
    public String checkRefundPolicy(@ToolParam(description = "The order ID to check") String orderId) {
        var result = refundData.checkRefundPolicy(orderId);
        var sb = new StringBuilder("Refund Policy Check for Order " + result.orderId() + ":\n");
        sb.append("- Order Status: ").append(result.status()).append("\n");
        sb.append("- Return Policy: ").append(result.returnPolicy()).append("\n");
        if (result.eligible()) {
            sb.append("- Eligibility: ELIGIBLE\n");
            sb.append("- Eligible Amount: ").append(result.eligibleAmount()).append(" (full refund)\n");
            sb.append("- Refund Method: Original payment method\n");
            sb.append("- Processing Time: 5-7 business days after approval");
        } else {
            sb.append("- Eligibility: NOT ELIGIBLE\n");
            sb.append("- Reason: ").append(result.reason());
        }
        return sb.toString();
    }

    @Tool(description = "Initiate a refund for an order. Requires the order ID, amount, and reason.")
    public String initiateRefund(
            @ToolParam(description = "The order ID") String orderId,
            @ToolParam(description = "Amount to refund (e.g. '49.99')") String amount,
            @ToolParam(description = "Reason for refund") String reason) {
        var result = refundData.initiateRefund(orderId, amount, reason);
        if (result.refundId() == null) return result.status() + ": " + result.reason();
        return "Refund Initiated:\n"
                + "- Order: " + result.orderId() + "\n"
                + "- Refund ID: " + result.refundId() + "\n"
                + "- Refund Amount: " + result.amount() + "\n"
                + "- Reason: " + result.reason() + "\n"
                + "- Status: " + result.status() + "\n"
                + "- Expected Completion: 5-7 business days\n"
                + "- Refund will be credited to the original payment method.";
    }

    @Tool(description = "Check the status of an existing refund by order ID.")
    public String checkRefundStatus(@ToolParam(description = "The order ID") String orderId) {
        var result = refundData.checkRefundStatus(orderId);
        if (result.refundId() == null) return result.status() + ": " + result.completionInfo();
        return "Refund Status:\n"
                + "- Order ID: " + result.orderId() + "\n"
                + "- Refund ID: " + result.refundId() + "\n"
                + "- Amount: " + result.amount() + "\n"
                + "- Status: " + result.status() + "\n"
                + "- " + result.completionInfo();
    }
}
```

- [ ] **Step 9: Add property to application.yml**

Add under `app:` section in `api/src/main/resources/application.yml`:

```yaml
app:
  ecommerce:
    provider: ${ECOMMERCE_PROVIDER:olist}
```

- [ ] **Step 10: Verify build compiles**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add agents/src/main/java/com/example/agents/tools/ api/src/main/resources/application.yml
git commit -m "refactor: introduce OrderDataProvider/RefundDataProvider with Olist and DummyJSON implementations"
```

---

## Task 8: MCP Server Module

**Files:**
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/EcommerceMcpApplication.java`
- Create: `ecommerce-mcp/src/main/resources/application.yml`
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/tools/McpProductTools.java`
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/tools/McpOrderTools.java`
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/tools/McpRefundTools.java`
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/tools/McpCustomerTools.java`
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/tools/McpReviewTools.java`
- Create: `ecommerce-mcp/src/main/java/com/example/ecommercemcp/config/McpServerConfig.java`

- [ ] **Step 1: Create application.yml for ecommerce-mcp**

```yaml
spring:
  ai:
    mcp:
      server:
        name: ecommerce-mcp
        version: 1.0.0

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:postgres}?prepareThreshold=0
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: ecommerce

server:
  port: ${MCP_SERVER_PORT:8081}
```

- [ ] **Step 2: Create EcommerceMcpApplication**

```java
package com.example.ecommercemcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.example.ecommerce.entity")
@EnableJpaRepositories(basePackages = "com.example.ecommerce.repository")
public class EcommerceMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceMcpApplication.class, args);
    }
}
```

- [ ] **Step 3: Create McpServerConfig**

```java
package com.example.ecommercemcp.config;

import com.example.ecommercemcp.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.example.ecommerce.service")
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            McpProductTools productTools,
            McpOrderTools orderTools,
            McpRefundTools refundTools,
            McpCustomerTools customerTools,
            McpReviewTools reviewTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(productTools, orderTools, refundTools, customerTools, reviewTools)
                .build();
    }
}
```

- [ ] **Step 4: Create McpProductTools**

```java
package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class McpProductTools {

    private final EcommerceProductService productService;

    public McpProductTools(EcommerceProductService productService) {
        this.productService = productService;
    }

    @Tool(description = "Search for products by category keyword. Returns matching products with id, category, and price.")
    public String searchProducts(
            @ToolParam(description = "Search query (matches category name)") String query,
            @ToolParam(description = "Filter by exact category name (optional)", required = false) String category,
            @ToolParam(description = "Max results to return (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var products = productService.searchProducts(query, category, maxResults);
        if (products.isEmpty()) return "No products found matching '" + query + "'.";
        var sb = new StringBuilder("Found " + products.size() + " products:\n");
        for (var p : products) {
            sb.append("- ID: ").append(p.getProductId())
                    .append(" | Category: ").append(p.getCategoryNameEn())
                    .append(" | Weight: ").append(p.getWeightG() != null ? p.getWeightG() + "g" : "N/A")
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "Get full details for a product by its product ID.")
    public String getProduct(@ToolParam(description = "The product ID") String productId) {
        try {
            var p = productService.getProduct(productId);
            var rating = productService.getAverageRating(productId);
            var reviewCount = productService.getReviewCount(productId);
            return "Product Details:\n"
                    + "- ID: " + p.getProductId() + "\n"
                    + "- Category: " + p.getCategoryNameEn() + "\n"
                    + "- Weight: " + (p.getWeightG() != null ? p.getWeightG() + "g" : "N/A") + "\n"
                    + "- Dimensions: " + (p.getLengthCm() != null ? p.getLengthCm() + "x" + p.getHeightCm() + "x" + p.getWidthCm() + " cm" : "N/A") + "\n"
                    + "- Photos: " + (p.getPhotosQty() != null ? p.getPhotosQty() : 0) + "\n"
                    + "- Avg Rating: " + (rating != null ? String.format("%.1f", rating) : "N/A") + "\n"
                    + "- Review Count: " + (reviewCount != null ? reviewCount : 0);
        } catch (Exception e) {
            return "PRODUCT_NOT_FOUND: No product exists with ID '" + productId + "'.";
        }
    }

    @Tool(description = "List all product categories with their English names and product counts.")
    public String listCategories() {
        var categories = productService.listCategories();
        if (categories.isEmpty()) return "No categories found.";
        var sb = new StringBuilder("Product Categories (" + categories.size() + "):\n");
        for (var row : categories) {
            sb.append("- ").append(row[0]).append(": ").append(row[1]).append(" products\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Create McpOrderTools**

```java
package com.example.ecommercemcp.tools;

import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.service.EcommerceOrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class McpOrderTools {

    private final EcommerceOrderService orderService;

    public McpOrderTools(EcommerceOrderService orderService) {
        this.orderService = orderService;
    }

    @Tool(description = "Get order details by order ID, including items, payments, and delivery dates.")
    public String getOrder(@ToolParam(description = "The order ID") String orderId) {
        try {
            var order = orderService.getOrder(orderId);
            String items = order.getItems().stream()
                    .map(i -> i.getProduct().getCategoryNameEn() + " ($" + i.getPrice() + " + freight $" + i.getFreightValue() + ")")
                    .collect(Collectors.joining(", "));
            String payments = order.getPayments().stream()
                    .map(p -> p.getPaymentType() + " $" + p.getPaymentValue() + " (" + p.getPaymentInstallments() + " installments)")
                    .collect(Collectors.joining(", "));
            return "Order Details:\n"
                    + "- Order ID: " + order.getOrderId() + "\n"
                    + "- Status: " + order.getStatus() + "\n"
                    + "- Customer ID: " + order.getCustomer().getCustomerId() + "\n"
                    + "- Items: " + items + "\n"
                    + "- Total: $" + order.getTotal().toPlainString() + "\n"
                    + "- Payment: " + payments + "\n"
                    + "- Purchased: " + order.getPurchaseTimestamp() + "\n"
                    + "- Estimated Delivery: " + order.getEstimatedDeliveryDate() + "\n"
                    + "- Delivered: " + (order.getDeliveredCustomerDate() != null ? order.getDeliveredCustomerDate() : "Not yet");
        } catch (OrderNotFoundException e) {
            return "ORDER_NOT_FOUND: " + e.getMessage();
        }
    }

    @Tool(description = "List recent orders for a customer, sorted by purchase date descending.")
    public String listCustomerOrders(
            @ToolParam(description = "The customer ID") String customerId,
            @ToolParam(description = "Max results (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var orders = orderService.listCustomerOrders(customerId, maxResults);
        if (orders.isEmpty()) return "No orders found for customer " + customerId + ".";
        var sb = new StringBuilder("Orders for customer " + customerId + ":\n");
        for (var o : orders) {
            sb.append("- Order #").append(o.getOrderId())
                    .append(" | $").append(o.getTotal().toPlainString())
                    .append(" | ").append(o.getStatus())
                    .append(" | ").append(o.getPurchaseTimestamp())
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "Track shipment for an order. Returns delivery dates and freight info.")
    public String trackShipment(@ToolParam(description = "The order ID to track") String orderId) {
        try {
            var order = orderService.getOrder(orderId);
            return "Shipment Tracking:\n"
                    + "- Order: " + order.getOrderId() + "\n"
                    + "- Status: " + order.getStatus() + "\n"
                    + "- Handed to Carrier: " + (order.getDeliveredCarrierDate() != null ? order.getDeliveredCarrierDate() : "Not yet") + "\n"
                    + "- Estimated Delivery: " + order.getEstimatedDeliveryDate() + "\n"
                    + "- Actual Delivery: " + (order.getDeliveredCustomerDate() != null ? order.getDeliveredCustomerDate() : "Not yet");
        } catch (OrderNotFoundException e) {
            return "ORDER_NOT_FOUND: " + e.getMessage();
        }
    }

    @Tool(description = "Cancel an order. Only pending/processing orders can be cancelled.")
    public String cancelOrder(
            @ToolParam(description = "The order ID to cancel") String orderId,
            @ToolParam(description = "Reason for cancellation") String reason) {
        try {
            var order = orderService.cancelOrder(orderId, reason);
            return "Order " + orderId + " cancelled. Reason: " + reason
                    + ". Refund of $" + order.getTotal().toPlainString() + " will be processed in 5-7 business days.";
        } catch (OrderNotFoundException e) {
            return "ORDER_NOT_FOUND: " + e.getMessage();
        } catch (OrderNotCancellableException e) {
            return "CANCEL_FAILED: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 6: Create McpRefundTools**

```java
package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceRefundService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class McpRefundTools {

    private final EcommerceRefundService refundService;

    public McpRefundTools(EcommerceRefundService refundService) {
        this.refundService = refundService;
    }

    @Tool(description = "Check if an order is eligible for a refund. Returns eligibility status and eligible amount.")
    public String checkRefundEligibility(@ToolParam(description = "The order ID to check") String orderId) {
        try {
            var result = refundService.checkEligibility(orderId);
            return "Refund Eligibility:\n"
                    + "- Order: " + orderId + "\n"
                    + "- Status: " + result.orderStatus() + "\n"
                    + "- Eligible: " + (result.eligible() ? "YES" : "NO") + "\n"
                    + "- Amount: $" + result.eligibleAmount().toPlainString() + "\n"
                    + "- " + result.reason();
        } catch (Exception e) {
            return "ORDER_NOT_FOUND: " + e.getMessage();
        }
    }

    @Tool(description = "Initiate a refund for a delivered order.")
    public String initiateRefund(
            @ToolParam(description = "The order ID") String orderId,
            @ToolParam(description = "Refund amount") String amount,
            @ToolParam(description = "Reason for refund") String reason) {
        try {
            var refund = refundService.initiateRefund(orderId, new BigDecimal(amount), reason);
            return "Refund Initiated:\n"
                    + "- Refund ID: " + refund.getRefundId() + "\n"
                    + "- Order: " + orderId + "\n"
                    + "- Amount: $" + refund.getAmount().toPlainString() + "\n"
                    + "- Status: " + refund.getStatus() + "\n"
                    + "- Expected: 5-7 business days";
        } catch (Exception e) {
            return "REFUND_FAILED: " + e.getMessage();
        }
    }

    @Tool(description = "Check the status of a refund by order ID.")
    public String getRefundStatus(@ToolParam(description = "The order ID") String orderId) {
        var refund = refundService.getRefundStatus(orderId);
        if (refund.isEmpty()) return "NO_REFUND: No refund found for order " + orderId;
        var r = refund.get();
        return "Refund Status:\n"
                + "- Refund ID: " + r.getRefundId() + "\n"
                + "- Order: " + orderId + "\n"
                + "- Amount: $" + r.getAmount().toPlainString() + "\n"
                + "- Status: " + r.getStatus() + "\n"
                + "- " + ("COMPLETED".equals(r.getStatus()) ? "Credited to original payment method" : "Processing — 5-7 business days");
    }
}
```

- [ ] **Step 7: Create McpCustomerTools**

```java
package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceCustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class McpCustomerTools {

    private final EcommerceCustomerService customerService;

    public McpCustomerTools(EcommerceCustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(description = "Get customer details by customer ID, including order count.")
    public String getCustomer(@ToolParam(description = "The customer ID") String customerId) {
        try {
            var c = customerService.getCustomer(customerId);
            long orderCount = customerService.getOrderCount(customerId);
            return "Customer Details:\n"
                    + "- ID: " + c.getCustomerId() + "\n"
                    + "- City: " + c.getCity() + "\n"
                    + "- State: " + c.getState() + "\n"
                    + "- Zip: " + c.getZipCodePrefix() + "\n"
                    + "- Total Orders: " + orderCount;
        } catch (Exception e) {
            return "CUSTOMER_NOT_FOUND: " + e.getMessage();
        }
    }

    @Tool(description = "Search customers by city or state name.")
    public String searchCustomers(
            @ToolParam(description = "Search query (city or state)") String query,
            @ToolParam(description = "Max results (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var customers = customerService.searchCustomers(query, maxResults);
        if (customers.isEmpty()) return "No customers found matching '" + query + "'.";
        var sb = new StringBuilder("Found " + customers.size() + " customers:\n");
        for (var c : customers) {
            sb.append("- ID: ").append(c.getCustomerId())
                    .append(" | ").append(c.getCity())
                    .append(", ").append(c.getState())
                    .append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 8: Create McpReviewTools**

```java
package com.example.ecommercemcp.tools;

import com.example.ecommerce.service.EcommerceReviewService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class McpReviewTools {

    private final EcommerceReviewService reviewService;

    public McpReviewTools(EcommerceReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Tool(description = "Get the review for a specific order.")
    public String getOrderReviews(@ToolParam(description = "The order ID") String orderId) {
        var review = reviewService.getOrderReview(orderId);
        if (review.isEmpty()) return "No review found for order " + orderId + ".";
        var r = review.get();
        return "Order Review:\n"
                + "- Order: " + orderId + "\n"
                + "- Score: " + r.getReviewScore() + "/5\n"
                + "- Title: " + (r.getReviewCommentTitle() != null ? r.getReviewCommentTitle() : "N/A") + "\n"
                + "- Comment: " + (r.getReviewCommentMessage() != null ? r.getReviewCommentMessage() : "N/A") + "\n"
                + "- Date: " + r.getReviewCreationDate();
    }

    @Tool(description = "Get reviews for a product across all orders that contain it.")
    public String getProductReviews(
            @ToolParam(description = "The product ID") String productId,
            @ToolParam(description = "Max results (default 10)", required = false) Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 10;
        var reviews = reviewService.getProductReviews(productId, maxResults);
        if (reviews.isEmpty()) return "No reviews found for product " + productId + ".";
        var sb = new StringBuilder("Reviews for product " + productId + " (" + reviews.size() + "):\n");
        for (var r : reviews) {
            sb.append("- Score: ").append(r.getReviewScore()).append("/5");
            if (r.getReviewCommentMessage() != null) {
                sb.append(" | ").append(r.getReviewCommentMessage().length() > 100
                        ? r.getReviewCommentMessage().substring(0, 100) + "..."
                        : r.getReviewCommentMessage());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 9: Verify build compiles**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add ecommerce-mcp/
git commit -m "feat: add ecommerce MCP server with SSE transport and 13 tools"
```

---

## Task 9: Integration Testing

**Files:**
- Create: `ecommerce/src/test/java/com/example/ecommerce/service/EcommerceOrderServiceTest.java`
- Create: `ecommerce/src/test/java/com/example/ecommerce/service/EcommerceRefundServiceTest.java`
- Create: `ecommerce/src/test/java/com/example/ecommerce/service/EcommerceProductServiceTest.java`

- [ ] **Step 1: Create EcommerceOrderServiceTest**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomCustomer;
import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.repository.EcomOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcommerceOrderServiceTest {

    @Mock
    private EcomOrderRepository orderRepo;

    private EcommerceOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new EcommerceOrderService(orderRepo);
    }

    @Test
    @DisplayName("getOrder throws when order not found")
    void getOrder_notFound_throws() {
        when(orderRepo.findByIdWithDetails("abc")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getOrder("abc"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("abc");
    }

    @Test
    @DisplayName("cancelOrder throws for shipped orders")
    void cancelOrder_shipped_throws() {
        var order = mock(EcomOrder.class);
        when(order.getStatus()).thenReturn("shipped");
        when(orderRepo.findById("abc")).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> orderService.cancelOrder("abc", "test"))
                .isInstanceOf(OrderNotCancellableException.class);
    }

    @Test
    @DisplayName("cancelOrder succeeds for processing orders")
    void cancelOrder_processing_succeeds() {
        var order = mock(EcomOrder.class);
        when(order.getStatus()).thenReturn("processing");
        when(orderRepo.findById("abc")).thenReturn(Optional.of(order));
        var result = orderService.cancelOrder("abc", "test");
        assertThat(result).isNotNull();
    }
}
```

- [ ] **Step 2: Create EcommerceRefundServiceTest**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.RefundNotEligibleException;
import com.example.ecommerce.repository.EcomOrderRepository;
import com.example.ecommerce.repository.EcomRefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcommerceRefundServiceTest {

    @Mock
    private EcomOrderRepository orderRepo;
    @Mock
    private EcomRefundRepository refundRepo;

    private EcommerceRefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new EcommerceRefundService(orderRepo, refundRepo);
    }

    @Test
    @DisplayName("checkEligibility returns eligible for delivered orders")
    void checkEligibility_delivered_eligible() {
        var order = mock(EcomOrder.class);
        when(order.getStatus()).thenReturn("delivered");
        when(order.getItems()).thenReturn(new ArrayList<>());
        when(order.getTotal()).thenReturn(BigDecimal.ZERO);
        when(orderRepo.findByIdWithDetails("abc")).thenReturn(Optional.of(order));
        var result = refundService.checkEligibility("abc");
        assertThat(result.eligible()).isTrue();
    }

    @Test
    @DisplayName("checkEligibility returns ineligible for shipped orders")
    void checkEligibility_shipped_ineligible() {
        var order = mock(EcomOrder.class);
        when(order.getStatus()).thenReturn("shipped");
        when(orderRepo.findByIdWithDetails("abc")).thenReturn(Optional.of(order));
        var result = refundService.checkEligibility("abc");
        assertThat(result.eligible()).isFalse();
    }

    @Test
    @DisplayName("initiateRefund throws for non-delivered orders")
    void initiateRefund_notDelivered_throws() {
        var order = mock(EcomOrder.class);
        when(order.getStatus()).thenReturn("shipped");
        when(orderRepo.findByIdWithDetails("abc")).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> refundService.initiateRefund("abc", BigDecimal.TEN, "test"))
                .isInstanceOf(RefundNotEligibleException.class);
    }
}
```

- [ ] **Step 3: Create EcommerceProductServiceTest**

```java
package com.example.ecommerce.service;

import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.EcomOrderReviewRepository;
import com.example.ecommerce.repository.EcomProductCategoryRepository;
import com.example.ecommerce.repository.EcomProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcommerceProductServiceTest {

    @Mock
    private EcomProductRepository productRepo;
    @Mock
    private EcomProductCategoryRepository categoryRepo;
    @Mock
    private EcomOrderReviewRepository reviewRepo;

    private EcommerceProductService productService;

    @BeforeEach
    void setUp() {
        productService = new EcommerceProductService(productRepo, categoryRepo, reviewRepo);
    }

    @Test
    @DisplayName("getProduct throws when not found")
    void getProduct_notFound_throws() {
        when(productRepo.findById("xyz")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.getProduct("xyz"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("xyz");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :ecommerce:test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add ecommerce/src/test/
git commit -m "test: add unit tests for ecommerce service layer"
```

---

## Task 10: Final Verification & Documentation

**Files:**
- Modify: `README.md` (add ecommerce module info)

- [ ] **Step 1: Full build verification**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Verify MCP server starts**

Run: `./gradlew :ecommerce-mcp:bootRun`
Expected: Application starts on port 8081, logs show MCP server initialized with SSE transport

- [ ] **Step 3: Verify main app still works with olist profile**

Run: `./gradlew :api:bootRun`
Expected: Application starts on port 8080, ecommerce services load (though no seed data unless `seed` profile is active)

- [ ] **Step 4: Verify main app works with dummyjson profile**

Run: `ECOMMERCE_PROVIDER=dummyjson ./gradlew :api:bootRun`
Expected: Application starts, DummyJSON data providers are active

- [ ] **Step 5: Commit all remaining changes**

```bash
git add .
git commit -m "feat: complete ecommerce integration with Olist dataset and MCP server"
```
