package com.example.agents.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Tools for order management operations.
 * Currently mock implementations — designed with clean interfaces
 * for future integration with real order management systems
 * (Shopify, WooCommerce, custom OMS).
 */
@Component
public class OrderTools {

    @Tool(description = "Look up an order by its order ID. Returns order details including status, items, total, and shipping information.")
    public String lookupOrder(
            @ToolParam(description = "The order ID to look up") String orderId) {

        // Mock implementation — replace with real OMS integration
        return formatOrderResponse(Map.of(
            "orderId", orderId,
            "status", "SHIPPED",
            "items", "2x Wireless Headphones, 1x Phone Case",
            "total", "$149.97",
            "orderDate", LocalDate.now().minusDays(3).toString(),
            "shippingAddress", "Customer's shipping address on file",
            "paymentMethod", "Credit card ending in ****"
        ));
    }

    @Tool(description = "Track the shipment for an order. Returns current shipping status, carrier, and estimated delivery date.")
    public String trackShipment(
            @ToolParam(description = "The order ID to track") String orderId) {

        return formatTrackingResponse(Map.of(
            "orderId", orderId,
            "carrier", "FedEx",
            "trackingNumber", "MOCK-TRK-" + orderId.hashCode(),
            "status", "In Transit",
            "currentLocation", "Distribution Center",
            "estimatedDelivery", LocalDate.now().plusDays(2).toString(),
            "lastUpdate", "Package departed facility"
        ));
    }

    @Tool(description = "Cancel an order. Only works for orders that haven't been shipped yet. Returns cancellation result.")
    public String cancelOrder(
            @ToolParam(description = "The order ID to cancel") String orderId,
            @ToolParam(description = "Reason for cancellation") String reason) {

        // Mock: simulate cancellation logic
        boolean canCancel = !orderId.contains("SHIPPED");

        if (canCancel) {
            return "Order " + orderId + " has been successfully cancelled. "
                + "Reason: " + reason + ". "
                + "A full refund of $149.97 will be processed within 5-7 business days.";
        } else {
            return "Order " + orderId + " cannot be cancelled because it has already been shipped. "
                + "You may return the items after delivery for a refund.";
        }
    }

    @Tool(description = "Get the list of recent orders for the current customer. Returns up to 5 most recent orders.")
    public String getRecentOrders(
            @ToolParam(description = "The customer ID") String customerId) {

        return """
            Recent orders for customer:
            1. ORD-1001 | $149.97 | SHIPPED | Wireless Headphones, Phone Case
            2. ORD-0998 | $29.99  | DELIVERED | USB-C Cable
            3. ORD-0985 | $299.00 | DELIVERED | Bluetooth Speaker
            """;
    }

    private String formatOrderResponse(Map<String, String> data) {
        return "Order Details:\n"
            + "- Order ID: " + data.get("orderId") + "\n"
            + "- Status: " + data.get("status") + "\n"
            + "- Items: " + data.get("items") + "\n"
            + "- Total: " + data.get("total") + "\n"
            + "- Order Date: " + data.get("orderDate") + "\n"
            + "- Shipping: " + data.get("shippingAddress") + "\n"
            + "- Payment: " + data.get("paymentMethod");
    }

    private String formatTrackingResponse(Map<String, String> data) {
        return "Shipment Tracking:\n"
            + "- Order: " + data.get("orderId") + "\n"
            + "- Carrier: " + data.get("carrier") + "\n"
            + "- Tracking #: " + data.get("trackingNumber") + "\n"
            + "- Status: " + data.get("status") + "\n"
            + "- Location: " + data.get("currentLocation") + "\n"
            + "- Est. Delivery: " + data.get("estimatedDelivery") + "\n"
            + "- Last Update: " + data.get("lastUpdate");
    }
}
