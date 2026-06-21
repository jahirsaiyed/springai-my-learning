package com.example.agents.tools;

public interface OrderDataProvider {

    record OrderDetails(String orderId, String status, String items, String total,
                        String customerId, String purchaseDate, String deliveryDate,
                        String estimatedDeliveryDate, String paymentInfo) {}

    record ShipmentDetails(String orderId, String status, String estimatedDelivery,
                           String actualDelivery, String carrierDate, String freightInfo) {}

    record OrderSummary(String orderId, String total, String status, String items) {}

    OrderDetails getOrder(String orderId);
    ShipmentDetails trackShipment(String orderId);
    String cancelOrder(String orderId, String reason);
    java.util.List<OrderSummary> getRecentOrders(String customerId);
}
