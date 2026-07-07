package com.phu.ecommerceapi.cart.application;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        long cartId,
        BigDecimal total,
        String currency,
        List<CartItemResponse> items
) {
}
