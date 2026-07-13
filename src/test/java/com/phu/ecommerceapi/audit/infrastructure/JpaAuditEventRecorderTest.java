package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditHashPayload;
import com.phu.ecommerceapi.audit.application.AuditHashService;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaAuditEventRecorderTest {

    private static final String FIRST_EVENT_HASH =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String SECOND_EVENT_HASH =
            "2222222222222222222222222222222222222222222222222222222222222222";

    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
    private final AuditHashChainStateRepository chainStateRepository = mock(AuditHashChainStateRepository.class);
    private final AuditHashService auditHashService = mock(AuditHashService.class);
    private final BusinessMetrics businessMetrics = mock(BusinessMetrics.class);
    private final JpaAuditEventRecorder recorder = new JpaAuditEventRecorder(
            auditEventRepository,
            chainStateRepository,
            auditHashService,
            businessMetrics
    );

    @Test
    void firstAuditEventUsesNullPreviousHashFromChainState() {
        AuditHashChainStateRecord chainState = new AuditHashChainStateRecord();
        when(chainStateRepository.findForUpdateById((short) 1)).thenReturn(Optional.of(chainState));
        when(auditHashService.hash(any(AuditHashPayload.class))).thenReturn(FIRST_EVENT_HASH);

        recorder.record(command("PAYMENT_SUCCEEDED", "payment-1"));

        AuditEventRecord savedEvent = savedEvent();
        assertThat(savedEvent.getPreviousHash()).isNull();
        assertThat(savedEvent.getEventHash()).isEqualTo(FIRST_EVENT_HASH);
        assertThat(chainState.getLatestHash()).isEqualTo(FIRST_EVENT_HASH);
        verify(auditEventRepository, never()).count();
    }

    @Test
    void secondAuditEventUsesLatestHashFromChainState() {
        AuditHashChainStateRecord chainState = new AuditHashChainStateRecord();
        chainState.markLatestHash(FIRST_EVENT_HASH, Instant.parse("2026-07-13T00:00:00Z"));
        when(chainStateRepository.findForUpdateById((short) 1)).thenReturn(Optional.of(chainState));
        when(auditHashService.hash(any(AuditHashPayload.class))).thenReturn(SECOND_EVENT_HASH);

        recorder.record(command("REFUND_SUCCEEDED", "refund-1"));

        AuditEventRecord savedEvent = savedEvent();
        assertThat(savedEvent.getPreviousHash()).isEqualTo(FIRST_EVENT_HASH);
        assertThat(savedEvent.getEventHash()).isEqualTo(SECOND_EVENT_HASH);
        assertThat(chainState.getLatestHash()).isEqualTo(SECOND_EVENT_HASH);
        verify(auditEventRepository, never()).count();
    }

    private AuditEventRecord savedEvent() {
        ArgumentCaptor<AuditEventRecord> eventCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventRepository).save(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private AuditEventCommand command(String action, String resourceId) {
        return new AuditEventCommand(
                "audit-actor",
                action,
                "PAYMENT",
                resourceId,
                "amount=20.00 USD"
        );
    }
}
