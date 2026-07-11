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
    private final LedgerEntryRepository entryRepository;

    public JpaLedgerPostingPersistenceAdapter(
            LedgerAccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Override
    public UUID postIfAbsent(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            List<LedgerEntryDraft> entries
    ) {
        UUID transactionId = UUID.randomUUID();
        int inserted = transactionRepository.insertIfAbsent(
                transactionId,
                transactionType.name(),
                referenceType,
                referenceId,
                description,
                OffsetDateTime.now()
        );
        if (inserted == 0) {
            return existingTransactionId(transactionType, referenceType, referenceId);
        }

        createEntries(transactionId, entries);
        return transactionId;
    }

    private void createEntries(
            UUID transactionId,
            List<LedgerEntryDraft> entries
    ) {
        LedgerTransactionRecord transaction = transactionRepository.getReferenceById(transactionId);
        List<LedgerEntryRecord> ledgerEntries = entries.stream()
                .map(entry -> new LedgerEntryRecord(
                        transaction,
                        account(entry),
                        entry.direction(),
                        entry.amount(),
                        entry.currency()
                ))
                .toList();
        entryRepository.saveAll(ledgerEntries);
    }

    private UUID existingTransactionId(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId
    ) {
        return transactionRepository.findIdByReference(referenceType, referenceId, transactionType)
                .orElseThrow(() -> new IllegalStateException("Ledger transaction insert conflict could not be read"));
    }

    private LedgerAccountRecord account(LedgerEntryDraft entry) {
        return accountRepository.findByCode(entry.accountCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ledger account not found: " + entry.accountCode()
                ));
    }
}
