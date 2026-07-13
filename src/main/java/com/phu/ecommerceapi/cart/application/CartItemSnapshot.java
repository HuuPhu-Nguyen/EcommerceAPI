package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.shared.domain.Money;

import java.util.Objects;

public record CartItemSnapshot(
        long productId,
        String productName,
        int quantity,
        Money unitPrice,
        Money lineTotal,
        boolean active
) {

    public CartItemSnapshot {
        Objects.requireNonNull(productName, "cart item product name is required");
        Objects.requireNonNull(unitPrice, "cart item unit price is required");
        Objects.requireNonNull(lineTotal, "cart item line total is required");
    }
}
