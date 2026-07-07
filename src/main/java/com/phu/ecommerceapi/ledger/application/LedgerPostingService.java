package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerBalanceValidator;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class LedgerPostingService {

    private final LedgerPostingPersistencePort persistencePort;

    public LedgerPostingService(LedgerPostingPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Transactional
    public UUID postTransaction(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            List<LedgerEntryDraft> entries
    ) {
        Objects.requireNonNull(transactionType, "ledger transaction type is required");
        String normalizedReferenceType = requireText(referenceType, "ledger reference type").toUpperCase(Locale.ROOT);
        String normalizedReferenceId = requireText(referenceId, "ledger reference id");
        String normalizedDescription = requireText(description, "ledger transaction description");
        validateBalanced(entries);

        return persistencePort.postIfAbsent(
                transactionType,
                normalizedReferenceType,
                normalizedReferenceId,
                normalizedDescription,
                entries
        );
    }

    private void validateBalanced(List<LedgerEntryDraft> entries) {
        LedgerBalanceValidator.requireBalanced(entries == null ? null : entries.stream()
                .map(entry -> Objects.requireNonNull(entry, "ledger entry is required").toLedgerEntryLine())
                .toList());
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
