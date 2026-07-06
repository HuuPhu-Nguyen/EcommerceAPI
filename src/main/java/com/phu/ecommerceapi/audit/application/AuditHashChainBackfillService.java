package com.phu.ecommerceapi.audit.application;

import com.phu.ecommerceapi.audit.infrastructure.AuditEventRecord;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.audit.infrastructure.AuditHashChainStateRecord;
import com.phu.ecommerceapi.audit.infrastructure.AuditHashChainStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AuditHashChainBackfillService {

    private static final short CHAIN_STATE_ID = 1;

    private final AuditEventRepository auditEventRepository;
    private final AuditHashChainStateRepository chainStateRepository;
    private final AuditHashService auditHashService;

    public AuditHashChainBackfillService(
            AuditEventRepository auditEventRepository,
            AuditHashChainStateRepository chainStateRepository,
            AuditHashService auditHashService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.chainStateRepository = chainStateRepository;
        this.auditHashService = auditHashService;
    }

    @Transactional
    public void initializeLegacyChain() {
        AuditHashChainStateRecord chainState = chainStateRepository.findForUpdateById(CHAIN_STATE_ID)
                .orElseThrow(() -> new IllegalStateException("Audit hash chain state is missing"));
        if (chainState.getLatestHash() != null) {
            return;
        }

        List<AuditEventRecord> events = auditEventRepository.findAllByOrderByIdAsc();
        if (events.isEmpty()) {
            return;
        }

        String previousHash = null;
        for (AuditEventRecord event : events) {
            if (event.getPreviousHash() != null || event.getEventHash() != null) {
                throw new IllegalStateException("Audit hash chain state is inconsistent with existing audit hashes");
            }
            String eventHash = auditHashService.hash(event.toHashPayload(previousHash));
            event.applyHash(previousHash, eventHash);
            previousHash = eventHash;
        }

        chainState.markLatestHash(previousHash, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
