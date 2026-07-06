package com.phu.ecommerceapi.shared.domain;

public record CartId(long value) {

    public CartId {
        if (value <= 0) {
            throw new IllegalArgumentException("Cart id must be positive");
        }
    }
}
