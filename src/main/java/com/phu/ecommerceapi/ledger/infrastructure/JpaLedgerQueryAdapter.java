package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.application.LedgerEntryView;
import com.phu.ecommerceapi.ledger.application.LedgerQueryPort;
import com.phu.ecommerceapi.ledger.application.LedgerTransactionView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaLedgerQueryAdapter implements LedgerQueryPort {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public JpaLedgerQueryAdapter(
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerTransactionView> recentTransactions(int limit) {
        return ledgerTransactionRepository.findRecentTransactions(PageRequest.of(0, limit))
                .stream()
                .map(this::toView)
                .toList();
    }

    private LedgerTransactionView toView(LedgerTransactionRecord transaction) {
        List<LedgerEntryView> entries = ledgerEntryRepository.findByTransactionId(transaction.getId())
                .stream()
                .map(this::toView)
                .toList();

        return new LedgerTransactionView(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getDescription(),
                transaction.getPostedAt(),
                entries
        );
    }

    private LedgerEntryView toView(LedgerEntryRecord entry) {
        LedgerAccountRecord account = entry.getAccount();
        return new LedgerEntryView(
                entry.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrency()
        );
    }
}
