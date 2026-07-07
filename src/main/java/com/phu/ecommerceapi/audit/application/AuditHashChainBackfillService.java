package com.phu.ecommerceapi.audit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AuditHashChainBackfillService {

    private final AuditEventPersistencePort auditEventPersistencePort;
    private final AuditHashService auditHashService;

    public AuditHashChainBackfillService(
            AuditEventPersistencePort auditEventPersistencePort,
            AuditHashService auditHashService
    ) {
        this.auditEventPersistencePort = auditEventPersistencePort;
        this.auditHashService = auditHashService;
    }

    @Transactional
    public void initializeLegacyChain() {
        if (auditEventPersistencePort.latestHashForUpdate() != null) {
            return;
        }

        List<AuditEventView> events = auditEventPersistencePort.findAllEventsByIdAsc();
        if (events.isEmpty()) {
            return;
        }

        String previousHash = null;
        for (AuditEventView event : events) {
            if (event.previousHash() != null || event.eventHash() != null) {
                throw new IllegalStateException("Audit hash chain state is inconsistent with existing audit hashes");
            }
            String eventHash = auditHashService.hash(event.toHashPayload(previousHash));
            auditEventPersistencePort.applyHash(event.id(), previousHash, eventHash);
            previousHash = eventHash;
        }

        auditEventPersistencePort.markLatestHash(previousHash, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
