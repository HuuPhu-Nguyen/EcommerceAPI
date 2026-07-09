package com.phu.ecommerceapi.payment.infrastructure;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record StripePaymentIntentCreateRequest(
        UUID paymentId,
        UUID orderId,
        long amountMinorUnits,
        String currency,
        String paymentMethodToken,
        String idempotencyKey,
        Map<String, String> metadata
) {

    public StripePaymentIntentCreateRequest {
        Objects.requireNonNull(paymentId, "payment id is required");
        Objects.requireNonNull(orderId, "order id is required");
        if (amountMinorUnits <= 0) {
            throw new IllegalArgumentException("Stripe amount must be positive minor units");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Stripe currency is required");
        }
        currency = currency.trim().toLowerCase(Locale.ROOT);
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("Stripe payment method token is required");
        }
        paymentMethodToken = paymentMethodToken.trim();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Stripe idempotency key is required");
        }
        idempotencyKey = idempotencyKey.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
