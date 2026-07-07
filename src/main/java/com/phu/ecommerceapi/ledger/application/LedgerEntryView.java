package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerAccountType;
import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;

import java.math.BigDecimal;
import java.util.Objects;

public record LedgerEntryView(
        Long id,
        String accountCode,
        String accountName,
        LedgerAccountType accountType,
        LedgerEntryDirection direction,
        BigDecimal amount,
        String currency
) {

    public LedgerEntryView {
        accountCode = requireText(accountCode, "ledger account code");
        accountName = requireText(accountName, "ledger account name");
        Objects.requireNonNull(accountType, "ledger account type is required");
        Objects.requireNonNull(direction, "ledger entry direction is required");
        Objects.requireNonNull(amount, "ledger entry amount is required");
        currency = requireText(currency, "ledger entry currency");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
