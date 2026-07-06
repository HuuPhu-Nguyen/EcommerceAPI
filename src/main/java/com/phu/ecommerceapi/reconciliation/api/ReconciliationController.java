package com.phu.ecommerceapi.reconciliation.api;

import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReport;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/report")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_RECONCILIATION_READ)
    public ReconciliationReport report() {
        return reconciliationService.runReport();
    }
}
