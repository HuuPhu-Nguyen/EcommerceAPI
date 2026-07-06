package com.phu.ecommerceapi.audit.application;

import com.phu.ecommerceapi.audit.infrastructure.AuditEventRecord;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AuditHashVerificationService {

    private final AuditEventRepository auditEventRepository;
    private final AuditHashService auditHashService;

    public AuditHashVerificationService(
            AuditEventRepository auditEventRepository,
            AuditHashService auditHashService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditHashService = auditHashService;
    }

    @Transactional(readOnly = true)
    public AuditHashVerificationResult verify() {
        List<AuditEventRecord> events = auditEventRepository.findAllByOrderByIdAsc();
        String expectedPreviousHash = null;
        long checkedEvents = 0;

        for (AuditEventRecord event : events) {
            checkedEvents++;
            if (event.getEventHash() == null || event.getEventHash().isBlank()) {
                return AuditHashVerificationResult.broken(
                        checkedEvents,
                        event.getId(),
                        "Audit event is missing hash"
                );
            }
            if (!Objects.equals(event.getPreviousHash(), expectedPreviousHash)) {
                return AuditHashVerificationResult.broken(
                        checkedEvents,
                        event.getId(),
                        "Audit event previous hash does not match chain"
                );
            }

            String expectedHash = auditHashService.hash(event.toHashPayload(expectedPreviousHash));
            if (!event.getEventHash().equals(expectedHash)) {
                return AuditHashVerificationResult.broken(
                        checkedEvents,
                        event.getId(),
                        "Audit event hash mismatch"
                );
            }
            expectedPreviousHash = event.getEventHash();
        }

        return AuditHashVerificationResult.verified(checkedEvents, expectedPreviousHash);
    }
}
