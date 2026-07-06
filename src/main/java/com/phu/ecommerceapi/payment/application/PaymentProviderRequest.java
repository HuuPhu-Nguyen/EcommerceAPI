package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PaymentProviderRequest(
        UUID orderId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        Map<String, String> metadata
) {

    public PaymentProviderRequest {
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(amount, "payment amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Payment currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Payment currency must be an ISO 4217 code");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Payment provider idempotency key is required");
        }
        idempotencyKey = idempotencyKey.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
