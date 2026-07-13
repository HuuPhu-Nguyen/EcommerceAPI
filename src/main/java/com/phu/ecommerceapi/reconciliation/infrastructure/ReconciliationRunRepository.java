package com.phu.ecommerceapi.reconciliation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRunRecord, UUID> {

    Optional<ReconciliationRunRecord> findFirstByStatusOrderByCompletedAtDesc(ReconciliationRunStatus status);

    Optional<ReconciliationRunRecord> findFirstByStatusOrderByStartedAtDesc(ReconciliationRunStatus status);
}
