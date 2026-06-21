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
