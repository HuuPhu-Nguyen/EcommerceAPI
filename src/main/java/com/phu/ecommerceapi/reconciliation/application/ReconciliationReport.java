package com.phu.ecommerceapi.reconciliation.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReconciliationReport(
        boolean healthy,
        Instant generatedAt,
        long checkedPayments,
        long checkedRefunds,
        long checkedLedgerTransactions,
        List<ReconciliationIssue> issues
) {

    public ReconciliationReport {
        Objects.requireNonNull(generatedAt, "reconciliation report generation time is required");
        issues = List.copyOf(Objects.requireNonNull(issues, "reconciliation issues are required"));
    }
}
