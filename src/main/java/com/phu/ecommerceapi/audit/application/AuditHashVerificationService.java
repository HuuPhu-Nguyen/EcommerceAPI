package com.phu.ecommerceapi.audit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AuditHashVerificationService {

    private final AuditEventPersistencePort auditEventPersistencePort;
    private final AuditHashService auditHashService;
    private final AuditHashVerificationProperties properties;

    public AuditHashVerificationService(
            AuditEventPersistencePort auditEventPersistencePort,
            AuditHashService auditHashService,
            AuditHashVerificationProperties properties
    ) {
        this.auditEventPersistencePort = auditEventPersistencePort;
        this.auditHashService = auditHashService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AuditHashVerificationResult verify() {
        String expectedPreviousHash = null;
        long checkedEvents = 0;
        long afterIdExclusive = 0;

        while (true) {
            List<AuditEventView> events = auditEventPersistencePort.findEventsAfterId(
                    afterIdExclusive,
                    properties.batchSize()
            );
            if (events.isEmpty()) {
                return AuditHashVerificationResult.verified(checkedEvents, expectedPreviousHash);
            }
            for (AuditEventView event : events) {
                checkedEvents++;
                if (event.eventHash() == null || event.eventHash().isBlank()) {
                    return AuditHashVerificationResult.broken(
                            checkedEvents,
                            event.id(),
                            "Audit event is missing hash"
                    );
                }
                if (!Objects.equals(event.previousHash(), expectedPreviousHash)) {
                    return AuditHashVerificationResult.broken(
                            checkedEvents,
                            event.id(),
                            "Audit event previous hash does not match chain"
                    );
                }

                String expectedHash = auditHashService.hash(event.toHashPayload(expectedPreviousHash));
                if (!event.eventHash().equals(expectedHash)) {
                    return AuditHashVerificationResult.broken(
                            checkedEvents,
                            event.id(),
                            "Audit event hash mismatch"
                    );
                }
                if (event.eventSignature() != null && event.eventSignature().isBlank()) {
                    return AuditHashVerificationResult.broken(
                            checkedEvents,
                            event.id(),
                            "Audit event signature is blank"
                    );
                }
                if (event.eventSignature() != null
                        && !auditHashService.signatureMatches(event.eventHash(), event.eventSignature())) {
                    return AuditHashVerificationResult.broken(
                            checkedEvents,
                            event.id(),
                            "Audit event signature mismatch"
                    );
                }
                expectedPreviousHash = event.eventHash();
            }
            afterIdExclusive = events.getLast().id();
        }
    }
}
