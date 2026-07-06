package com.phu.ecommerceapi.ledger.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccountRecord, UUID> {

    Optional<LedgerAccountRecord> findByCode(String code);
}
