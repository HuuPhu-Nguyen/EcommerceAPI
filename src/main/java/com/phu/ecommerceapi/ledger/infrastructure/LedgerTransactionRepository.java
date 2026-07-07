package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.reconciliation.application.LedgerTransactionReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionRecord, UUID> {

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.LedgerTransactionReconciliationItem(
                ledgerTransaction.id,
                ledgerTransaction.transactionType,
                ledgerTransaction.referenceType,
                ledgerTransaction.referenceId
            )
            from LedgerTransactionRecord ledgerTransaction
            """)
    List<LedgerTransactionReconciliationItem> findAllForReconciliation();

    @Query("""
            select ledgerTransaction
            from LedgerTransactionRecord ledgerTransaction
            order by ledgerTransaction.postedAt desc
            """)
    List<LedgerTransactionRecord> findRecentTransactions(Pageable pageable);

    Optional<LedgerTransactionRecord> findByReferenceTypeAndReferenceIdAndTransactionType(
            String referenceType,
            String referenceId,
            LedgerTransactionType transactionType
    );
}
