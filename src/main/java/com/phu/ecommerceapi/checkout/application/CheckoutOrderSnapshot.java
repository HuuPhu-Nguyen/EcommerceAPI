package com.phu.ecommerceapi.checkout.application;

import com.phu.ecommerceapi.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CheckoutOrderSnapshot(
        UUID id,
        long cartId,
        long customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        OffsetDateTime createdAt,
        List<CheckoutOrderItemSnapshot> items
) {

    public CheckoutOrderSnapshot {
        Objects.requireNonNull(id, "checkout order id is required");
        Objects.requireNonNull(status, "checkout order status is required");
        Objects.requireNonNull(totalAmount, "checkout order total is required");
        Objects.requireNonNull(currency, "checkout order currency is required");
        Objects.requireNonNull(createdAt, "checkout order creation time is required");
        items = List.copyOf(Objects.requireNonNull(items, "checkout order items are required"));
    }
}
