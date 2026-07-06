package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionRecord, UUID> {

    Optional<LedgerTransactionRecord> findByReferenceTypeAndReferenceIdAndTransactionType(
            String referenceType,
            String referenceId,
            LedgerTransactionType transactionType
    );
}
