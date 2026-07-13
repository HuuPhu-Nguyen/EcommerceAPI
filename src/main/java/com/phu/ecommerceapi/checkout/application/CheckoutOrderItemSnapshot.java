package com.phu.ecommerceapi.checkout.application;

import java.math.BigDecimal;
import java.util.Objects;

public record CheckoutOrderItemSnapshot(
        long productId,
        String productName,
        int quantity,
        BigDecimal unitPriceAmount,
        BigDecimal lineTotalAmount
) {

    public CheckoutOrderItemSnapshot {
        Objects.requireNonNull(productName, "order item product name is required");
        Objects.requireNonNull(unitPriceAmount, "order item unit price amount is required");
        Objects.requireNonNull(lineTotalAmount, "order item line total amount is required");
    }
}
