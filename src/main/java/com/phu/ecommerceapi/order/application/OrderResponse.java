package com.phu.ecommerceapi.order.application;

import com.phu.ecommerceapi.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        long cartId,
        long customerId,
        OrderStatus status,
        BigDecimal total,
        String currency,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items
) {
}
