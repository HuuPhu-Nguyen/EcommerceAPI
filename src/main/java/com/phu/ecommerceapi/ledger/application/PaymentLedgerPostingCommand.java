package com.phu.ecommerceapi.ledger.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PaymentLedgerPostingCommand(
        UUID paymentId,
        UUID orderId,
        long customerId,
        BigDecimal amount,
        String currency,
        String providerPaymentId
) {

    public PaymentLedgerPostingCommand {
        Objects.requireNonNull(paymentId, "payment id is required");
        Objects.requireNonNull(orderId, "order id is required");
        if (customerId <= 0) {
            throw new IllegalArgumentException("customer id must be positive");
        }
        Objects.requireNonNull(amount, "payment amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("payment amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("payment currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("provider payment id is required");
        }
        providerPaymentId = providerPaymentId.trim();
    }
}
