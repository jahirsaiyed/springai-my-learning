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
                "INSERT INTO ecommerce.order_payments (order_id, payment_sequential, payment_type, payment_installments, payment_value) VALUES (?, ?, ?, ?, ?::numeric) ON CONFLICT DO NOTHING",
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
            throw new IllegalStateException("Failed to read seed CSV: " + resourcePath, e);
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
