package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.reconciliation.application.LedgerTransactionReconciliationItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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
            where (:afterIdExclusive is null or ledgerTransaction.id > :afterIdExclusive)
            order by ledgerTransaction.id asc
            """)
    List<LedgerTransactionReconciliationItem> findPageForReconciliation(
            @Param("afterIdExclusive") UUID afterIdExclusive,
            Pageable pageable
    );

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.LedgerTransactionReconciliationItem(
                ledgerTransaction.id,
                ledgerTransaction.transactionType,
                ledgerTransaction.referenceType,
                ledgerTransaction.referenceId
            )
            from LedgerTransactionRecord ledgerTransaction
            where ledgerTransaction.transactionType in :transactionTypes
              and ledgerTransaction.referenceType in :referenceTypes
              and ledgerTransaction.referenceId in :referenceIds
            """)
    List<LedgerTransactionReconciliationItem> findByReferenceCandidatesForReconciliation(
            @Param("transactionTypes") List<LedgerTransactionType> transactionTypes,
            @Param("referenceTypes") List<String> referenceTypes,
            @Param("referenceIds") List<String> referenceIds
    );

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

    @Query("""
            select ledgerTransaction.id
            from LedgerTransactionRecord ledgerTransaction
            where ledgerTransaction.referenceType = :referenceType
              and ledgerTransaction.referenceId = :referenceId
              and ledgerTransaction.transactionType = :transactionType
            """)
    Optional<UUID> findIdByReference(
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId,
            @Param("transactionType") LedgerTransactionType transactionType
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    insert into ledger_transaction (
                        id,
                        transaction_type,
                        reference_type,
                        reference_id,
                        description,
                        posted_at
                    )
                    values (
                        :id,
                        :transactionType,
                        :referenceType,
                        :referenceId,
                        :description,
                        :postedAt
                    )
                    on conflict (reference_type, reference_id, transaction_type) do nothing
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("transactionType") String transactionType,
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId,
            @Param("description") String description,
            @Param("postedAt") OffsetDateTime postedAt
    );
}
