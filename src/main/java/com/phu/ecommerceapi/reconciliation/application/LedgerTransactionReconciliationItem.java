package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;

import java.util.UUID;

public record LedgerTransactionReconciliationItem(
        UUID id,
        LedgerTransactionType transactionType,
        String referenceType,
        String referenceId
) {
}
