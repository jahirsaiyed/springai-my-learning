package com.example.agents.tools.olist;

import com.example.agents.tools.OrderDataProvider;
import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.service.EcommerceOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "olist", matchIfMissing = true)
public class OlistOrderDataProvider implements OrderDataProvider {

    private final EcommerceOrderService orderService;

    public OlistOrderDataProvider(EcommerceOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderDetails getOrder(String orderId) {
        var order = orderService.getOrder(orderId);
        String items = order.getItems().stream()
                .map(i -> i.getProduct().getCategoryNameEn() + " ($" + i.getPrice() + ")")
                .collect(Collectors.joining(", "));
        String paymentInfo = order.getPayments().stream()
                .map(p -> p.getPaymentType() + " $" + p.getPaymentValue())
                .collect(Collectors.joining(", "));
        return new OrderDetails(
                order.getOrderId(), order.getStatus(), items,
                "$" + order.getTotal().toPlainString(),
                order.getCustomer().getCustomerId(),
                order.getPurchaseTimestamp() != null ? order.getPurchaseTimestamp().toString() : "N/A",
                order.getDeliveredCustomerDate() != null ? order.getDeliveredCustomerDate().toString() : "Not yet delivered",
                order.getEstimatedDeliveryDate() != null ? order.getEstimatedDeliveryDate().toString() : "N/A",
                paymentInfo);
    }

    @Override
    public ShipmentDetails trackShipment(String orderId) {
        var order = orderService.getOrder(orderId);
        String freightInfo = order.getItems().stream()
                .map(i -> "freight: $" + i.getFreightValue())
                .collect(Collectors.joining(", "));
        return new ShipmentDetails(
                order.getOrderId(), order.getStatus(),
                order.getEstimatedDeliveryDate() != null ? order.getEstimatedDeliveryDate().toString() : "N/A",
                order.getDeliveredCustomerDate() != null ? order.getDeliveredCustomerDate().toString() : "Not yet delivered",
                order.getDeliveredCarrierDate() != null ? order.getDeliveredCarrierDate().toString() : "Not yet handed to carrier",
                freightInfo);
    }

    @Override
    public String cancelOrder(String orderId, String reason) {
        try {
            var order = orderService.cancelOrder(orderId, reason);
            return "Order " + orderId + " has been successfully cancelled. Reason: " + reason
                    + ". A full refund of " + order.getTotal().toPlainString()
                    + " will be processed within 5-7 business days.";
        } catch (OrderNotCancellableException e) {
            return e.getMessage();
        } catch (OrderNotFoundException e) {
            return "ORDER_NOT_FOUND: " + e.getMessage();
        }
    }

    @Override
    public List<OrderSummary> getRecentOrders(String customerId) {
        return orderService.listCustomerOrders(customerId, 10).stream()
                .map(o -> new OrderSummary(o.getOrderId(),
                        "$" + o.getTotal().toPlainString(),
                        o.getStatus(), ""))
                .toList();
    }
}
