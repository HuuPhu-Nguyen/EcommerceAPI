package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditHashChainBackfillService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuditHashChainInitializer implements ApplicationRunner {

    private final AuditHashChainBackfillService backfillService;

    public AuditHashChainInitializer(AuditHashChainBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillService.initializeLegacyChain();
    }
}
