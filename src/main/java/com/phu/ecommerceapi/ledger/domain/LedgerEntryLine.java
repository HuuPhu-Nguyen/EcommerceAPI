package com.phu.ecommerceapi.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

public record LedgerEntryLine(
        LedgerEntryDirection direction,
        BigDecimal amount,
        String currency
) {

    public LedgerEntryLine {
        Objects.requireNonNull(direction, "ledger entry direction is required");
        Objects.requireNonNull(amount, "ledger entry amount is required");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("ledger entry amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("ledger entry currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new IllegalArgumentException("ledger entry currency must be an ISO 4217 code");
        }
    }
}
