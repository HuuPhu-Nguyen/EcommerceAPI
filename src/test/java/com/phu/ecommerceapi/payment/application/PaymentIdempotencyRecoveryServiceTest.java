package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentIdempotencyRecoveryServiceTest {

    private final PaymentIdempotencyRecoveryPort recoveryPort = mock(PaymentIdempotencyRecoveryPort.class);
    private final PaymentAttemptService paymentAttemptService = mock(PaymentAttemptService.class);
    private final RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
    private final AuditEventRecorder auditEventRecorder = mock(AuditEventRecorder.class);

    private PaymentIdempotencyRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new PaymentIdempotencyRecoveryService(
                new ObjectMapper(),
                recoveryPort,
                paymentAttemptService,
                refundAttemptService,
                auditEventRecorder
        );
    }

    @Test
    void recoversStuckStripePaymentIdempotencyFromLocalSuccessWithoutProviderCreateCall() {
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(10L, "PAYMENT", paymentId, "stripe", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.of(paymentResponse(
                paymentId,
                "stripe",
                "SUCCEEDED",
                "pi_recovered"
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(recoveryPort).completeRecovered(eq(10L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue()).contains("\"status\":\"SUCCEEDED\"", "\"providerPaymentId\":\"pi_recovered\"");
        verify(auditEventRecorder).record(any(AuditEventCommand.class));
    }

    @Test
    void recoversStuckStripeRefundIdempotencyFromLocalSuccessWithoutProviderRefundCreateCall() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(11L, "REFUND", refundId, "stripe", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(refundAttemptService.findAttemptResponse(refundId)).thenReturn(Optional.of(refundResponse(
                refundId,
                paymentId,
                "stripe",
                "SUCCEEDED",
                "re_recovered"
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(recoveryPort).completeRecovered(eq(11L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue()).contains("\"status\":\"SUCCEEDED\"", "\"providerRefundId\":\"re_recovered\"");
        verify(paymentAttemptService, never()).findAttemptResponse(any(UUID.class));
    }

    @Test
    void unresolvedStripeTimeoutIsMarkedManualReviewAndAuditDoesNotLeakProviderKey() {
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(12L, "PAYMENT", paymentId, "stripe", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.of(paymentResponse(
                paymentId,
                "stripe",
                "PROVIDER_TIMEOUT",
                null
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        verify(recoveryPort).markManualReview(eq(12L), any(OffsetDateTime.class));
        verify(recoveryPort, never()).completeRecovered(
                eq(12L),
                any(Integer.class),
                any(String.class),
                any(OffsetDateTime.class)
        );

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventRecorder).record(audit.capture());
        assertThat(audit.getValue().details())
                .contains("provider=stripe", "recoveryStatus=PROVIDER_TIMEOUT")
                .doesNotContain("secret-provider-key");
    }

    private PaymentIdempotencyRecoveryEntry entry(
            long recordId,
            String resourceType,
            UUID resourceId,
            String providerCode,
            String providerIdempotencyKey
    ) {
        return new PaymentIdempotencyRecoveryEntry(
                recordId,
                resourceType,
                resourceId,
                providerCode,
                providerIdempotencyKey
        );
    }

    private PaymentAttemptResponse paymentResponse(
            UUID paymentId,
            String provider,
            String status,
            String providerPaymentId
    ) {
        return new PaymentAttemptResponse(
                paymentId,
                UUID.randomUUID(),
                provider,
                status,
                status.toLowerCase(),
                providerPaymentId,
                null,
                "local state",
                new BigDecimal("25.00"),
                "USD"
        );
    }

    private RefundAttemptResponse refundResponse(
            UUID refundId,
            UUID paymentId,
            String provider,
            String status,
            String providerRefundId
    ) {
        return new RefundAttemptResponse(
                refundId,
                paymentId,
                UUID.randomUUID(),
                provider,
                status,
                status.toLowerCase(),
                providerRefundId,
                null,
                "local state",
                new BigDecimal("25.00"),
                "USD"
        );
    }
}
