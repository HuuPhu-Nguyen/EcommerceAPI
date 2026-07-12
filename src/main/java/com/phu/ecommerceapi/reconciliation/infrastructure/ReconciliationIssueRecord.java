package com.phu.ecommerceapi.reconciliation.infrastructure;

import com.phu.ecommerceapi.reconciliation.application.ReconciliationIssue;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationIssueCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "reconciliation_issue_record")
public class ReconciliationIssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRunRecord run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private ReconciliationIssueCode code;

    @Column(nullable = false, length = 100)
    private String resourceType;

    @Column(length = 255)
    private String resourceId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    protected ReconciliationIssueRecord() {
    }

    private ReconciliationIssueRecord(
            ReconciliationRunRecord run,
            ReconciliationIssue issue,
            Instant createdAt
    ) {
        this.run = Objects.requireNonNull(run, "reconciliation run is required");
        this.code = Objects.requireNonNull(issue.code(), "reconciliation issue code is required");
        this.resourceType = requireText(issue.resourceType(), "reconciliation issue resource type");
        this.resourceId = trimToNull(issue.resourceId());
        this.message = requireText(issue.message(), "reconciliation issue message");
        this.createdAt = Objects.requireNonNull(createdAt, "reconciliation issue creation time is required");
    }

    public static ReconciliationIssueRecord from(
            ReconciliationRunRecord run,
            ReconciliationIssue issue,
            Instant createdAt
    ) {
        return new ReconciliationIssueRecord(run, issue, createdAt);
    }

    public ReconciliationIssue toIssue() {
        return new ReconciliationIssue(code, resourceType, resourceId, message);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
