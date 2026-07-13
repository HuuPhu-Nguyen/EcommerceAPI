package com.phu.ecommerceapi.catalog.application;

import com.phu.ecommerceapi.shared.domain.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record CartProductSnapshot(
        long id,
        String name,
        Money price,
        boolean active
) {

    public CartProductSnapshot {
        Objects.requireNonNull(name, "product name is required");
        Objects.requireNonNull(price, "product price is required");
    }

    public CartProductSnapshot(long id, String name, BigDecimal price, String currency, boolean active) {
        this(id, name, Money.of(price, currency), active);
    }
}
