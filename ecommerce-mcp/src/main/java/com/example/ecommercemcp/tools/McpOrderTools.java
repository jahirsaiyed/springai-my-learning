package com.example.ecommercemcp.tools;

import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.service.EcommerceOrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
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
    @Transactional
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
