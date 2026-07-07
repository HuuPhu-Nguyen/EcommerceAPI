package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;

import java.util.List;
import java.util.UUID;

public interface LedgerPostingPersistencePort {

    UUID postIfAbsent(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            List<LedgerEntryDraft> entries
    );
}
