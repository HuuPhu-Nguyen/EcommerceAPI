package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryReconciliationItem(
        UUID transactionId,
        LedgerEntryDirection direction,
        BigDecimal amount,
        String currency,
        String accountCode
) {
}
