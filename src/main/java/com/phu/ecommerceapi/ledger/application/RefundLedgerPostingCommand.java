package com.phu.ecommerceapi.ledger.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record RefundLedgerPostingCommand(
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        long customerId,
        BigDecimal amount,
        String currency,
        String providerRefundId
) {

    public RefundLedgerPostingCommand {
        Objects.requireNonNull(refundId, "refund id is required");
        Objects.requireNonNull(paymentId, "payment id is required");
        Objects.requireNonNull(orderId, "order id is required");
        if (customerId <= 0) {
            throw new IllegalArgumentException("customer id must be positive");
        }
        Objects.requireNonNull(amount, "refund amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("refund currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (providerRefundId == null || providerRefundId.isBlank()) {
            throw new IllegalArgumentException("provider refund id is required");
        }
        providerRefundId = providerRefundId.trim();
    }
}
