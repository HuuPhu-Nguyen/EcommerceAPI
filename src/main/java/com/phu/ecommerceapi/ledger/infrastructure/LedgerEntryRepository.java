package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.reconciliation.application.LedgerEntryReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryRecord, Long> {

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.LedgerEntryReconciliationItem(
                ledgerTransaction.id,
                entry.direction,
                entry.amount,
                entry.currency,
                account.code
            )
            from LedgerEntryRecord entry
            join entry.transaction ledgerTransaction
            join entry.account account
            """)
    List<LedgerEntryReconciliationItem> findAllForReconciliation();

    List<LedgerEntryRecord> findByTransactionId(UUID transactionId);
}
