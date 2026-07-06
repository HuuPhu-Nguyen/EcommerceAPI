package com.phu.ecommerceapi.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "Payment id is required");
    }

    public static PaymentId newId() {
        return new PaymentId(UUID.randomUUID());
    }
}
