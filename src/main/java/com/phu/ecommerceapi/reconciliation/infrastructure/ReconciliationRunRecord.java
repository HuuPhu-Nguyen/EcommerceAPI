package com.phu.ecommerceapi.reconciliation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRunRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationRunStatus status;

    private Boolean healthy;

    @Column(nullable = false)
    private long paymentCount;

    @Column(nullable = false)
    private long refundCount;

    @Column(nullable = false)
    private long ledgerTransactionCount;

    @Column(nullable = false)
    private long issueCount;

    @Column(length = 1000)
    private String failureMessage;

    protected ReconciliationRunRecord() {
    }

    private ReconciliationRunRecord(Instant startedAt) {
        this.id = UUID.randomUUID();
        this.startedAt = Objects.requireNonNull(startedAt, "reconciliation run start time is required");
        this.status = ReconciliationRunStatus.RUNNING;
    }

    public static ReconciliationRunRecord running(Instant startedAt) {
        return new ReconciliationRunRecord(startedAt);
    }

    public void complete(
            Instant completedAt,
            boolean healthy,
            long paymentCount,
            long refundCount,
            long ledgerTransactionCount,
            long issueCount
    ) {
        this.completedAt = Objects.requireNonNull(completedAt, "reconciliation run completion time is required");
        this.status = ReconciliationRunStatus.COMPLETED;
        this.healthy = healthy;
        this.paymentCount = paymentCount;
        this.refundCount = refundCount;
        this.ledgerTransactionCount = ledgerTransactionCount;
        this.issueCount = issueCount;
        this.failureMessage = null;
    }

    public void fail(Instant completedAt, String failureMessage) {
        this.completedAt = Objects.requireNonNull(completedAt, "reconciliation run completion time is required");
        this.status = ReconciliationRunStatus.FAILED;
        this.failureMessage = trimToLength(failureMessage, 1000);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Boolean getHealthy() {
        return healthy;
    }

    public ReconciliationRunStatus getStatus() {
        return status;
    }

    public long getPaymentCount() {
        return paymentCount;
    }

    public long getRefundCount() {
        return refundCount;
    }

    public long getLedgerTransactionCount() {
        return ledgerTransactionCount;
    }

    public long getIssueCount() {
        return issueCount;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
