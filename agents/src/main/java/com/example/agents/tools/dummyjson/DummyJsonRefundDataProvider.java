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
