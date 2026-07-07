package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionView(
        UUID transactionId,
        LedgerTransactionType transactionType,
        String referenceType,
        String referenceId,
        String description,
        OffsetDateTime postedAt,
        List<LedgerEntryView> entries
) {

    public LedgerTransactionView {
        Objects.requireNonNull(transactionId, "ledger transaction id is required");
        Objects.requireNonNull(transactionType, "ledger transaction type is required");
        referenceType = requireText(referenceType, "ledger reference type");
        referenceId = requireText(referenceId, "ledger reference id");
        description = requireText(description, "ledger transaction description");
        Objects.requireNonNull(postedAt, "ledger posted time is required");
        entries = List.copyOf(Objects.requireNonNull(entries, "ledger entries are required"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
