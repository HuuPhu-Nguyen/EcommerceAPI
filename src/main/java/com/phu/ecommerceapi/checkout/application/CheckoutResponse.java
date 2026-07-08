package com.phu.ecommerceapi.checkout.application;

import com.phu.ecommerceapi.order.application.OrderItemResponse;
import com.phu.ecommerceapi.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        long cartId,
        long customerId,
        OrderStatus status,
        BigDecimal total,
        String currency,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items,
        List<String> allowedPaymentProviders
) {
    public CheckoutResponse {
        items = List.copyOf(items);
        allowedPaymentProviders = List.copyOf(allowedPaymentProviders);
    }
}
