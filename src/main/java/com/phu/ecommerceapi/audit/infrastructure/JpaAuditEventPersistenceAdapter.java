package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditEventPersistencePort;
import com.phu.ecommerceapi.audit.application.AuditEventView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class JpaAuditEventPersistenceAdapter implements AuditEventPersistencePort {

    private static final short CHAIN_STATE_ID = 1;

    private final AuditEventRepository auditEventRepository;
    private final AuditHashChainStateRepository chainStateRepository;

    public JpaAuditEventPersistenceAdapter(
            AuditEventRepository auditEventRepository,
            AuditHashChainStateRepository chainStateRepository
    ) {
        this.auditEventRepository = auditEventRepository;
        this.chainStateRepository = chainStateRepository;
    }

    @Override
    public List<AuditEventView> findRecentEvents(int limit) {
        return auditEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public List<AuditEventView> findEventsAfterId(long afterIdExclusive, int limit) {
        return auditEventRepository.findByIdGreaterThanOrderByIdAsc(
                        afterIdExclusive,
                        PageRequest.of(0, Math.max(1, limit))
                )
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public String latestHashForUpdate() {
        return chainStateForUpdate().getLatestHash();
    }

    @Override
    public void applyHash(long eventId, String previousHash, String eventHash) {
        AuditEventRecord event = auditEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Audit event not found"));
        event.applyHash(previousHash, eventHash);
    }

    @Override
    public void markLatestHash(String latestHash, Instant updatedAt) {
        chainStateForUpdate().markLatestHash(latestHash, updatedAt);
    }

    private AuditHashChainStateRecord chainStateForUpdate() {
        return chainStateRepository.findForUpdateById(CHAIN_STATE_ID)
                .orElseThrow(() -> new IllegalStateException("Audit hash chain state is missing"));
    }

    private AuditEventView toView(AuditEventRecord event) {
        return new AuditEventView(
                event.getId(),
                event.getActorSubject(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getDetails(),
                event.getRequestId(),
                event.getExternalCorrelationId(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getCreatedAt(),
                event.getPreviousHash(),
                event.getEventHash()
        );
    }
}
