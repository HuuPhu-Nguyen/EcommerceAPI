package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.application.LedgerEntryDraft;
import com.phu.ecommerceapi.ledger.application.LedgerPostingPersistencePort;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class JpaLedgerPostingPersistenceAdapter implements LedgerPostingPersistencePort {

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    public JpaLedgerPostingPersistenceAdapter(
            LedgerAccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public UUID postIfAbsent(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            List<LedgerEntryDraft> entries
    ) {
        return transactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(referenceType, referenceId, transactionType)
                .map(LedgerTransactionRecord::getId)
                .orElseGet(() -> createTransaction(transactionType, referenceType, referenceId, description, entries));
    }

    private UUID createTransaction(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            List<LedgerEntryDraft> entries
    ) {
        LedgerTransactionRecord transaction = LedgerTransactionRecord.posted(
                transactionType,
                referenceType,
                referenceId,
                description,
                OffsetDateTime.now()
        );

        for (LedgerEntryDraft entry : entries) {
            LedgerAccountRecord account = accountRepository.findByCode(entry.accountCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Ledger account not found: " + entry.accountCode()
                    ));
            transaction.addEntry(account, entry.direction(), entry.amount(), entry.currency());
        }

        return transactionRepository.saveAndFlush(transaction).getId();
    }
}
