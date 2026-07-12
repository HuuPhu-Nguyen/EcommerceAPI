package com.phu.ecommerceapi.reconciliation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunStorePort {

    UUID startRun(Instant startedAt);

    void completeRun(UUID runId, ReconciliationRunCompletion completion, List<ReconciliationIssue> storedIssues);

    void failRun(UUID runId, Instant completedAt, String failureMessage);

    Optional<ReconciliationReport> findCompleted(UUID runId, int issueLimit);

    Optional<ReconciliationReport> findLatestCompleted(int issueLimit);
}
