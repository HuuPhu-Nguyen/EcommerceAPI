package com.phu.ecommerceapi.cart.application;

import java.math.BigDecimal;

public record CartItemResponse(
        long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal lineTotal
) {
}
