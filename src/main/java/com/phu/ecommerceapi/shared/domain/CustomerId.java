package com.phu.ecommerceapi.shared.domain;

public record CustomerId(long value) {

    public CustomerId {
        if (value <= 0) {
            throw new IllegalArgumentException("Customer id must be positive");
        }
    }
}
