package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PaymentRefundProviderRequest(
        UUID paymentId,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        Map<String, String> metadata
) {

    public PaymentRefundProviderRequest {
        Objects.requireNonNull(paymentId, "payment id is required");
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("provider payment id is required");
        }
        providerPaymentId = providerPaymentId.trim();
        Objects.requireNonNull(amount, "refund amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Refund currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Refund currency must be an ISO 4217 code");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Refund provider idempotency key is required");
        }
        idempotencyKey = idempotencyKey.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
