package com.phu.ecommerceapi.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionId(UUID value) {

    public LedgerTransactionId {
        Objects.requireNonNull(value, "Ledger transaction id is required");
    }

    public static LedgerTransactionId newId() {
        return new LedgerTransactionId(UUID.randomUUID());
    }
}
