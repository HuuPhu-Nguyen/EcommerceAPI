package com.phu.ecommerceapi.reconciliation.application;

import java.time.Instant;

public record ReconciliationRunCompletion(
        Instant completedAt,
        boolean healthy,
        long paymentCount,
        long refundCount,
        long ledgerTransactionCount,
        long issueCount
) {
}
