package com.phu.ecommerceapi.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "Order id is required");
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }
}
