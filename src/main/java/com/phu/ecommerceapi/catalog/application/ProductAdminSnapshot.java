package com.phu.ecommerceapi.catalog.application;

import java.math.BigDecimal;
import java.util.Objects;

public record ProductAdminSnapshot(
        long id,
        String name,
        BigDecimal price,
        String currency,
        boolean active
) {

    public ProductAdminSnapshot {
        Objects.requireNonNull(name, "product name is required");
        Objects.requireNonNull(price, "product price is required");
        Objects.requireNonNull(currency, "product currency is required");
    }
}
