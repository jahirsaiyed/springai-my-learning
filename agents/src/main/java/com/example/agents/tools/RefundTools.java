package com.example.agents.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools for refund processing operations.
 * Mock implementations — designed for integration with payment gateways
 * (Stripe, PayPal, etc.).
 */
@Component
public class RefundTools {

    @Tool(description = "Check if an order is eligible for a refund based on the return policy. Returns eligibility status and details.")
    public String checkRefundPolicy(
            @ToolParam(description = "The order ID to check") String orderId) {

        // Mock: 30-day return window
        return "Refund Policy Check for order " + orderId + ":\n"
            + "- Return Window: 30 days from delivery\n"
            + "- Status: ELIGIBLE\n"
            + "- Eligible Amount: $149.97 (full refund)\n"
            + "- Refund Method: Original payment method\n"
            + "- Condition: Items must be unused and in original packaging\n"
            + "- Processing Time: 5-7 business days after approval";
    }

    @Tool(description = "Initiate a refund for an order. Requires the order ID, refund amount, and reason. Returns refund confirmation.")
    public String initiateRefund(
            @ToolParam(description = "The order ID") String orderId,
            @ToolParam(description = "Amount to refund") String amount,
            @ToolParam(description = "Reason for refund") String reason) {

        return "Refund Initiated:\n"
            + "- Order: " + orderId + "\n"
            + "- Refund Amount: " + amount + "\n"
            + "- Reason: " + reason + "\n"
            + "- Refund ID: REF-" + Math.abs(orderId.hashCode()) + "\n"
            + "- Status: PROCESSING\n"
            + "- Expected Completion: 5-7 business days\n"
            + "- Refund will be credited to the original payment method.";
    }

    @Tool(description = "Check the status of an existing refund by order ID or refund ID.")
    public String checkRefundStatus(
            @ToolParam(description = "The order ID or refund ID") String referenceId) {

        return "Refund Status:\n"
            + "- Reference: " + referenceId + "\n"
            + "- Status: PROCESSING\n"
            + "- Amount: $149.97\n"
            + "- Initiated: 2 days ago\n"
            + "- Expected Completion: 3-5 more business days";
    }
}
