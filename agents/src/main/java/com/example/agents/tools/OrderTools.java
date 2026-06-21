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
