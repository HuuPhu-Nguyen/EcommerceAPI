package com.phu.ecommerceapi.reconciliation.infrastructure;

import com.phu.ecommerceapi.reconciliation.application.ReconciliationIssue;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReport;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationRunCompletion;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationRunStorePort;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaReconciliationRunStoreAdapter implements ReconciliationRunStorePort {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationIssueRecordRepository issueRepository;

    public JpaReconciliationRunStoreAdapter(
            ReconciliationRunRepository runRepository,
            ReconciliationIssueRecordRepository issueRepository
    ) {
        this.runRepository = runRepository;
        this.issueRepository = issueRepository;
    }

    @Override
    @Transactional
    public UUID startRun(Instant startedAt) {
        return runRepository.save(ReconciliationRunRecord.running(startedAt)).getId();
    }

    @Override
    @Transactional
    public void completeRun(
            UUID runId,
            ReconciliationRunCompletion completion,
            List<ReconciliationIssue> storedIssues
    ) {
        ReconciliationRunRecord run = findRun(runId);
        run.complete(
                completion.completedAt(),
                completion.healthy(),
                completion.paymentCount(),
                completion.refundCount(),
                completion.ledgerTransactionCount(),
                completion.issueCount()
        );
        List<ReconciliationIssueRecord> issueRecords = storedIssues.stream()
                .map(issue -> ReconciliationIssueRecord.from(run, issue, completion.completedAt()))
                .toList();
        issueRepository.saveAll(issueRecords);
    }

    @Override
    @Transactional
    public void failRun(UUID runId, Instant completedAt, String failureMessage) {
        findRun(runId).fail(completedAt, failureMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReconciliationReport> findCompleted(UUID runId, int issueLimit) {
        return runRepository.findById(runId)
                .filter(run -> run.getStatus() == ReconciliationRunStatus.COMPLETED)
                .map(run -> toReport(run, Math.max(1, issueLimit)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReconciliationReport> findLatestCompleted(int issueLimit) {
        return runRepository.findFirstByStatusOrderByCompletedAtDesc(ReconciliationRunStatus.COMPLETED)
                .map(run -> toReport(run, Math.max(1, issueLimit)));
    }

    private ReconciliationReport toReport(ReconciliationRunRecord run, int issueLimit) {
        List<ReconciliationIssue> issues = issueRepository
                .findByRunIdOrderByIdAsc(run.getId(), PageRequest.of(0, issueLimit))
                .stream()
                .map(ReconciliationIssueRecord::toIssue)
                .toList();
        return new ReconciliationReport(
                Boolean.TRUE.equals(run.getHealthy()),
                run.getCompletedAt(),
                run.getPaymentCount(),
                run.getRefundCount(),
                run.getLedgerTransactionCount(),
                run.getIssueCount(),
                run.getIssueCount() > issues.size(),
                issues
        );
    }

    private ReconciliationRunRecord findRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Reconciliation run not found"));
    }
}
