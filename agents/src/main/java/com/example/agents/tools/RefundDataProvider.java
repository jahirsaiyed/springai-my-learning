package com.example.agents.tools;

public interface RefundDataProvider {

    record RefundPolicyResult(String orderId, String status, String items,
                              boolean eligible, String eligibleAmount, String reason,
                              String returnPolicy) {}

    record RefundResult(String orderId, String refundId, String amount,
                        String status, String reason) {}

    record RefundStatusResult(String orderId, String refundId, String amount,
                              String status, String completionInfo) {}

    RefundPolicyResult checkRefundPolicy(String orderId);
    RefundResult initiateRefund(String orderId, String amount, String reason);
    RefundStatusResult checkRefundStatus(String orderId);
}
