package com.phu.ecommerceapi.payment.api;

import java.util.Objects;
import java.util.UUID;

public record CreatePaymentRequest(
        UUID orderId,
        String provider,
        String paymentMethodToken
) {

    public CreatePaymentRequest {
        Objects.requireNonNull(orderId, "order id is required");
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("payment method token is required");
        }
        provider = provider == null ? null : provider.trim();
        paymentMethodToken = paymentMethodToken.trim();
    }
}
