package com.phu.ecommerceapi.reconciliation.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reconciliation.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;
    private volatile ReconciliationReport latestReport;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:300000}")
    public void runScheduledReconciliation() {
        latestReport = reconciliationService.runReport();
    }

    public ReconciliationReport latestReport() {
        return latestReport;
    }
}
