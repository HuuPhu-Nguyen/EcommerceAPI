package com.phu.ecommerceapi.shared.domain;

public record Quantity(int value) {

    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }
}
