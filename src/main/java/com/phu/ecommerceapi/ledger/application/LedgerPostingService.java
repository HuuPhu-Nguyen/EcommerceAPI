package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerAccountRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerAccountRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LedgerPostingService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    public LedgerPostingService(
            LedgerAccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
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

        return transactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        normalizedReferenceType,
                        normalizedReferenceId,
                        transactionType
                )
                .map(LedgerTransactionRecord::getId)
                .orElseGet(() -> createTransaction(
                        transactionType,
                        normalizedReferenceType,
                        normalizedReferenceId,
                        normalizedDescription,
                        entries
                ));
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

    private void validateBalanced(List<LedgerEntryDraft> entries) {
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("ledger transaction requires at least two entries");
        }

        Map<String, Map<LedgerEntryDirection, BigDecimal>> totalsByCurrency = entries.stream()
                .collect(Collectors.groupingBy(
                        LedgerEntryDraft::currency,
                        Collectors.groupingBy(
                                LedgerEntryDraft::direction,
                                () -> new EnumMap<>(LedgerEntryDirection.class),
                                Collectors.reducing(BigDecimal.ZERO, LedgerEntryDraft::amount, BigDecimal::add)
                        )
                ));

        for (Map.Entry<String, Map<LedgerEntryDirection, BigDecimal>> currencyTotals : totalsByCurrency.entrySet()) {
            BigDecimal debits = currencyTotals.getValue().getOrDefault(LedgerEntryDirection.DEBIT, BigDecimal.ZERO);
            BigDecimal credits = currencyTotals.getValue().getOrDefault(LedgerEntryDirection.CREDIT, BigDecimal.ZERO);
            if (debits.compareTo(credits) != 0) {
                throw new IllegalArgumentException(
                        "ledger transaction is not balanced for " + currencyTotals.getKey()
                );
            }
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
