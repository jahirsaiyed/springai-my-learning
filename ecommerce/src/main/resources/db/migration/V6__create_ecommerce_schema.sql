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
