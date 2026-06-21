package com.example.agents.tools.olist;

import com.example.agents.tools.RefundDataProvider;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.exception.RefundNotEligibleException;
import com.example.ecommerce.service.EcommerceRefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "olist", matchIfMissing = true)
public class OlistRefundDataProvider implements RefundDataProvider {

    private final EcommerceRefundService refundService;

    public OlistRefundDataProvider(EcommerceRefundService refundService) {
        this.refundService = refundService;
    }

    @Override
    public RefundPolicyResult checkRefundPolicy(String orderId) {
        try {
            var result = refundService.checkEligibility(orderId);
            return new RefundPolicyResult(orderId, result.orderStatus(), "",
                    result.eligible(), "$" + result.eligibleAmount().toPlainString(),
                    result.reason(), "30 days return policy");
        } catch (OrderNotFoundException e) {
            return new RefundPolicyResult(orderId, "NOT_FOUND", "", false, "$0",
                    e.getMessage(), "");
        }
    }

    @Override
    public RefundResult initiateRefund(String orderId, String amount, String reason) {
        try {
            var refund = refundService.initiateRefund(orderId, new BigDecimal(amount), reason);
            return new RefundResult(orderId, refund.getRefundId(),
                    "$" + refund.getAmount().toPlainString(), refund.getStatus(), reason);
        } catch (OrderNotFoundException e) {
            return new RefundResult(orderId, null, null, "ORDER_NOT_FOUND", e.getMessage());
        } catch (RefundNotEligibleException e) {
            return new RefundResult(orderId, null, null, "NOT_ELIGIBLE", e.getMessage());
        }
    }

    @Override
    public RefundStatusResult checkRefundStatus(String orderId) {
        var refund = refundService.getRefundStatus(orderId);
        if (refund.isEmpty()) {
            return new RefundStatusResult(orderId, null, null, "NO_REFUND",
                    "No refund found for order " + orderId);
        }
        var r = refund.get();
        String completionInfo = "COMPLETED".equals(r.getStatus())
                ? "Refund has been credited to the original payment method."
                : "Refund is being processed. Expected completion: 5-7 business days.";
        return new RefundStatusResult(orderId, r.getRefundId(),
                "$" + r.getAmount().toPlainString(), r.getStatus(), completionInfo);
    }
}
