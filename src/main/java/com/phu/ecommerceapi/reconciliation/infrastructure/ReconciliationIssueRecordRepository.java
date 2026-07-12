package com.phu.ecommerceapi.reconciliation.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReconciliationIssueRecordRepository extends JpaRepository<ReconciliationIssueRecord, Long> {

    @Query("""
            select issue
            from ReconciliationIssueRecord issue
            where issue.run.id = :runId
            order by issue.id asc
            """)
    List<ReconciliationIssueRecord> findByRunIdOrderByIdAsc(@Param("runId") UUID runId, Pageable pageable);
}
