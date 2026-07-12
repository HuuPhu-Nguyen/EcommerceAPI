package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;

import java.util.Locale;
import java.util.Objects;

public record LedgerTransactionLookupKey(
        LedgerTransactionType transactionType,
        String referenceType,
        String referenceId
) {

    public LedgerTransactionLookupKey {
        Objects.requireNonNull(transactionType, "ledger transaction type is required");
        referenceType = normalizeReferenceType(referenceType);
        referenceId = requireText(referenceId, "ledger transaction reference id");
    }

    private static String normalizeReferenceType(String value) {
        return requireText(value, "ledger transaction reference type").toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
