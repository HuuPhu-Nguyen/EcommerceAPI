package com.phu.ecommerceapi.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record RefundId(UUID value) {

    public RefundId {
        Objects.requireNonNull(value, "Refund id is required");
    }

    public static RefundId newId() {
        return new RefundId(UUID.randomUUID());
    }
}
