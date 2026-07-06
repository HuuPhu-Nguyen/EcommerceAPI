package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditHashService;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.shared.api.RequestMetadataHolder;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JpaAuditEventRecorder implements AuditEventRecorder {

    private static final short CHAIN_STATE_ID = 1;

    private final AuditEventRepository auditEventRepository;
    private final AuditHashChainStateRepository chainStateRepository;
    private final AuditHashService auditHashService;
    private final BusinessMetrics businessMetrics;

    public JpaAuditEventRecorder(
            AuditEventRepository auditEventRepository,
            AuditHashChainStateRepository chainStateRepository,
            AuditHashService auditHashService,
            BusinessMetrics businessMetrics
    ) {
        this.auditEventRepository = auditEventRepository;
        this.chainStateRepository = chainStateRepository;
        this.auditHashService = auditHashService;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public void record(AuditEventCommand command) {
        try {
            AuditHashChainStateRecord chainState = chainStateRepository.findForUpdateById(CHAIN_STATE_ID)
                    .orElseThrow(() -> new IllegalStateException("Audit hash chain state is missing"));
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            AuditEventRecord event = AuditEventRecord.from(command, RequestMetadataHolder.current(), now);
            String previousHash = auditEventRepository.count() == 0 ? null : chainState.getLatestHash();
            String eventHash = auditHashService.hash(event.toHashPayload(previousHash));
            event.applyHash(previousHash, eventHash);
            auditEventRepository.save(event);
            chainState.markLatestHash(eventHash, now);
            businessMetrics.auditWrite(action(command), "success");
        } catch (RuntimeException exception) {
            businessMetrics.auditWrite(action(command), "failure");
            throw exception;
        }
    }

    private String action(AuditEventCommand command) {
        return command == null ? "unknown" : command.action();
    }
}
