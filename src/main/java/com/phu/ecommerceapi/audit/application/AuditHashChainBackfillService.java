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
    private final AuditHashVerificationProperties properties;

    public AuditHashChainBackfillService(
            AuditEventPersistencePort auditEventPersistencePort,
            AuditHashService auditHashService,
            AuditHashVerificationProperties properties
    ) {
        this.auditEventPersistencePort = auditEventPersistencePort;
        this.auditHashService = auditHashService;
        this.properties = properties;
    }

    @Transactional
    public void initializeLegacyChain() {
        if (auditEventPersistencePort.latestHashForUpdate() != null) {
            return;
        }

        String previousHash = null;
        long afterIdExclusive = 0;
        boolean appliedAnyHash = false;
        while (true) {
            List<AuditEventView> events = auditEventPersistencePort.findEventsAfterId(
                    afterIdExclusive,
                    properties.batchSize()
            );
            if (events.isEmpty()) {
                break;
            }
            for (AuditEventView event : events) {
                if (event.previousHash() != null || event.eventHash() != null) {
                    throw new IllegalStateException("Audit hash chain state is inconsistent with existing audit hashes");
                }
                String eventHash = auditHashService.hash(event.toHashPayload(previousHash));
                auditEventPersistencePort.applyHash(event.id(), previousHash, eventHash);
                previousHash = eventHash;
                appliedAnyHash = true;
            }
            afterIdExclusive = events.getLast().id();
        }

        if (appliedAnyHash) {
            auditEventPersistencePort.markLatestHash(previousHash, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }
    }
}
