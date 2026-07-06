package com.phu.ecommerceapi.ledger.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryRecord, Long> {

    List<LedgerEntryRecord> findByTransactionId(UUID transactionId);
}
