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
