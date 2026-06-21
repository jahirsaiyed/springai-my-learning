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
