package com.phu.ecommerceapi.payment.infrastructure;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record StripeRefundCreateRequest(
        UUID refundId,
        UUID paymentId,
        String paymentIntentId,
        long amountMinorUnits,
        String currency,
        String idempotencyKey,
        Map<String, String> metadata
) {

    public StripeRefundCreateRequest {
        Objects.requireNonNull(refundId, "refund id is required");
        Objects.requireNonNull(paymentId, "payment id is required");
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Stripe PaymentIntent id is required");
        }
        paymentIntentId = paymentIntentId.trim();
        if (amountMinorUnits <= 0) {
            throw new IllegalArgumentException("Stripe refund amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Stripe refund currency is required");
        }
        currency = currency.trim().toLowerCase();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Stripe refund idempotency key is required");
        }
        idempotencyKey = idempotencyKey.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
