package com.phu.ecommerceapi.payment.api;

import java.util.Objects;
import java.util.UUID;

public record CreatePaymentRequest(
        UUID orderId,
        String provider,
        String paymentMethodToken
) {
    private static final int MAX_PROVIDER_LENGTH = 32;
    private static final int MAX_PAYMENT_METHOD_TOKEN_LENGTH = 128;

    public CreatePaymentRequest {
        Objects.requireNonNull(orderId, "order id is required");
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("payment method token is required");
        }
        provider = provider == null || provider.isBlank() ? null : provider.trim();
        if (provider != null && provider.length() > MAX_PROVIDER_LENGTH) {
            throw new IllegalArgumentException(
                    "payment provider must be " + MAX_PROVIDER_LENGTH + " characters or fewer"
            );
        }
        paymentMethodToken = paymentMethodToken.trim();
        if (paymentMethodToken.length() > MAX_PAYMENT_METHOD_TOKEN_LENGTH) {
            throw new IllegalArgumentException(
                    "payment method token must be " + MAX_PAYMENT_METHOD_TOKEN_LENGTH + " characters or fewer"
            );
        }
    }
}
