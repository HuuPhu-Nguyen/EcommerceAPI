package com.phu.ecommerceapi.order.application;

import java.math.BigDecimal;

public record OrderItemResponse(
        long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal lineTotal
) {
}
