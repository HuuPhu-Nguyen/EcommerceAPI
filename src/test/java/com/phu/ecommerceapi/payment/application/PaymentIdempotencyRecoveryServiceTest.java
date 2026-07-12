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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void recoversStuckFakePaymentIdempotencyFromLocalFailureWithoutProviderCreateCall() {
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(13L, "PAYMENT", paymentId, "fake", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.of(paymentResponse(
                paymentId,
                "fake",
                "FAILED",
                "fake_failed"
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(recoveryPort).completeRecovered(eq(13L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue()).contains("\"provider\":\"fake\"", "\"status\":\"FAILED\"");
        verify(refundAttemptService, never()).findAttemptResponse(any(UUID.class));
    }

    @Test
    void recoversStuckPaymentIdempotencyFromLocalPendingWithProviderReference() {
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(14L, "PAYMENT", paymentId, "stripe", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.of(paymentResponse(
                paymentId,
                "stripe",
                "PENDING",
                "pi_pending"
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(recoveryPort).completeRecovered(eq(14L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue()).contains("\"status\":\"PENDING\"", "\"providerPaymentId\":\"pi_pending\"");
    }

    @Test
    void recoversStuckPaymentIdempotencyByFinalizingDurableProviderSuccess() {
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(20L, "PAYMENT", paymentId, "fake", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.of(paymentResponse(
                paymentId,
                "fake",
                "PROVIDER_SUCCEEDED_LEDGER_PENDING",
                "fake_payment_recovered"
        )));
        when(paymentAttemptService.finalizeProviderSucceededPayment(paymentId, null)).thenReturn(paymentResponse(
                paymentId,
                "fake",
                "SUCCEEDED",
                "fake_payment_recovered"
        ));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(paymentAttemptService).finalizeProviderSucceededPayment(paymentId, null);
        verify(recoveryPort).completeRecovered(eq(20L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue())
                .contains("\"status\":\"SUCCEEDED\"", "\"providerPaymentId\":\"fake_payment_recovered\"");
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
    void recoversStuckFakeRefundIdempotencyFromLocalFailureWithoutProviderRefundCreateCall() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(15L, "REFUND", refundId, "fake", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(refundAttemptService.findAttemptResponse(refundId)).thenReturn(Optional.of(refundResponse(
                refundId,
                paymentId,
                "fake",
                "FAILED",
                "fake_refund_failed"
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(recoveryPort).completeRecovered(eq(15L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue()).contains("\"provider\":\"fake\"", "\"status\":\"FAILED\"");
        verify(paymentAttemptService, never()).findAttemptResponse(any(UUID.class));
    }

    @Test
    void recoversStuckRefundIdempotencyFromLocalPendingWithProviderReference() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(16L, "REFUND", refundId, "stripe", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(refundAttemptService.findAttemptResponse(refundId)).thenReturn(Optional.of(refundResponse(
                refundId,
                paymentId,
                "stripe",
                "PENDING",
                "re_pending"
        )));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(recoveryPort).completeRecovered(eq(16L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue()).contains("\"status\":\"PENDING\"", "\"providerRefundId\":\"re_pending\"");
        verify(paymentAttemptService, never()).findAttemptResponse(any(UUID.class));
    }

    @Test
    void recoversStuckRefundIdempotencyByFinalizingDurableProviderSuccess() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(21L, "REFUND", refundId, "fake", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(refundAttemptService.findAttemptResponse(refundId)).thenReturn(Optional.of(refundResponse(
                refundId,
                paymentId,
                "fake",
                "PROVIDER_SUCCEEDED_LEDGER_PENDING",
                "fake_refund_recovered"
        )));
        when(refundAttemptService.finalizeProviderSucceededRefund(refundId, null)).thenReturn(refundResponse(
                refundId,
                paymentId,
                "fake",
                "SUCCEEDED",
                "fake_refund_recovered"
        ));

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<String> responseBody = ArgumentCaptor.forClass(String.class);
        verify(refundAttemptService).finalizeProviderSucceededRefund(refundId, null);
        verify(recoveryPort).completeRecovered(eq(21L), eq(200), responseBody.capture(), any(OffsetDateTime.class));
        assertThat(responseBody.getValue())
                .contains("\"status\":\"SUCCEEDED\"", "\"providerRefundId\":\"fake_refund_recovered\"");
    }

    @Test
    void unresolvedStripeTimeoutIsMarkedPendingReconciliationAndAuditDoesNotLeakProviderKey() {
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
        verify(recoveryPort).markPendingReconciliation(eq(12L), any(OffsetDateTime.class));
        verify(recoveryPort, never()).markManualReview(eq(12L), any(OffsetDateTime.class));
        verify(recoveryPort, never()).completeRecovered(
                eq(12L),
                any(Integer.class),
                any(String.class),
                any(OffsetDateTime.class)
        );

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("PAYMENT_IDEMPOTENCY_PENDING_RECONCILIATION");
        assertThat(audit.getValue().details())
                .contains("provider=stripe", "recoveryStatus=PROVIDER_TIMEOUT")
                .doesNotContain("secret-provider-key");
    }

    @Test
    void missingLinkedPaymentIsMarkedManualReview() {
        UUID paymentId = UUID.randomUUID();
        PaymentIdempotencyRecoveryEntry entry = entry(17L, "PAYMENT", paymentId, "fake", "secret-provider-key");
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(100))).thenReturn(List.of(entry));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.empty());

        int processed = recoveryService.recoverExpired();

        assertThat(processed).isEqualTo(1);
        verify(recoveryPort).markManualReview(eq(17L), any(OffsetDateTime.class));
        verify(recoveryPort, never()).completeRecovered(
                eq(17L),
                any(Integer.class),
                any(String.class),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void recoversMultipleExpiredEntriesInOneClaimBatch() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID refundedPaymentId = UUID.randomUUID();
        when(recoveryPort.claimExpired(any(OffsetDateTime.class), eq(2))).thenReturn(List.of(
                entry(18L, "PAYMENT", paymentId, "fake", "payment-key"),
                entry(19L, "REFUND", refundId, "fake", "refund-key")
        ));
        when(paymentAttemptService.findAttemptResponse(paymentId)).thenReturn(Optional.of(paymentResponse(
                paymentId,
                "fake",
                "SUCCEEDED",
                "fake_payment"
        )));
        when(refundAttemptService.findAttemptResponse(refundId)).thenReturn(Optional.of(refundResponse(
                refundId,
                refundedPaymentId,
                "fake",
                "SUCCEEDED",
                "fake_refund"
        )));

        int processed = recoveryService.recoverExpired(2);

        assertThat(processed).isEqualTo(2);
        verify(recoveryPort, times(2)).completeRecovered(
                anyLong(),
                anyInt(),
                any(String.class),
                any(OffsetDateTime.class)
        );
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
