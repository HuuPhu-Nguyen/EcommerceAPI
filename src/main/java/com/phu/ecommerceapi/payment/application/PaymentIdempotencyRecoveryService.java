package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class PaymentIdempotencyRecoveryService {

    private static final int DEFAULT_RECOVERY_LIMIT = 100;
    private static final String PAYMENT_RESOURCE_TYPE = "PAYMENT";
    private static final String REFUND_RESOURCE_TYPE = "REFUND";
    private static final String IDEMPOTENCY_RESOURCE_TYPE = "PAYMENT_IDEMPOTENCY";
    private static final String SUCCEEDED_STATUS = "SUCCEEDED";
    private static final String FAILED_STATUS = "FAILED";
    private static final String PENDING_STATUS = "PENDING";
    private static final String PROVIDER_TIMEOUT_STATUS = "PROVIDER_TIMEOUT";
    private static final String REFUNDED_STATUS = "REFUNDED";

    private final ObjectMapper objectMapper;
    private final PaymentIdempotencyRecoveryPort recoveryPort;
    private final PaymentAttemptService paymentAttemptService;
    private final RefundAttemptService refundAttemptService;
    private final AuditEventRecorder auditEventRecorder;

    public PaymentIdempotencyRecoveryService(
            ObjectMapper objectMapper,
            PaymentIdempotencyRecoveryPort recoveryPort,
            PaymentAttemptService paymentAttemptService,
            RefundAttemptService refundAttemptService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.objectMapper = objectMapper;
        this.recoveryPort = recoveryPort;
        this.paymentAttemptService = paymentAttemptService;
        this.refundAttemptService = refundAttemptService;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public int recoverExpired() {
        return recoverExpired(DEFAULT_RECOVERY_LIMIT);
    }

    @Transactional
    public int recoverExpired(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        int processed = 0;
        for (PaymentIdempotencyRecoveryEntry entry : recoveryPort.claimExpired(now, Math.max(1, limit))) {
            recover(entry, now);
            processed++;
        }
        return processed;
    }

    private void recover(PaymentIdempotencyRecoveryEntry entry, OffsetDateTime now) {
        if (PAYMENT_RESOURCE_TYPE.equals(entry.resourceType())) {
            recoverPayment(entry, now);
            return;
        }
        if (REFUND_RESOURCE_TYPE.equals(entry.resourceType())) {
            recoverRefund(entry, now);
            return;
        }
        markManualReview(entry, now, "UNSUPPORTED_RESOURCE");
    }

    private void recoverPayment(PaymentIdempotencyRecoveryEntry entry, OffsetDateTime now) {
        Optional<PaymentAttemptResponse> attempt = paymentAttemptService.findAttemptResponse(entry.resourceId());
        if (attempt.isEmpty()) {
            markManualReview(entry, now, "MISSING_PAYMENT");
            return;
        }
        PaymentAttemptResponse response = attempt.get();
        if (PROVIDER_TIMEOUT_STATUS.equals(response.status())) {
            markPendingReconciliation(entry, now, "PROVIDER_TIMEOUT");
            return;
        }
        if (PENDING_STATUS.equals(response.status()) && isBlank(response.providerPaymentId())) {
            markManualReview(entry, now, "PENDING_WITHOUT_PROVIDER_PAYMENT_ID");
            return;
        }
        if (!isRecoverablePaymentStatus(response.status())) {
            markManualReview(entry, now, "UNSUPPORTED_PAYMENT_STATUS_" + response.status());
            return;
        }
        recoveryPort.completeRecovered(
                entry.recordId(),
                HttpStatus.OK.value(),
                serialize(response),
                now
        );
        recordAudit("PAYMENT_IDEMPOTENCY_RECOVERED", entry, "RECOVERED");
    }

    private void recoverRefund(PaymentIdempotencyRecoveryEntry entry, OffsetDateTime now) {
        Optional<RefundAttemptResponse> attempt = refundAttemptService.findAttemptResponse(entry.resourceId());
        if (attempt.isEmpty()) {
            markManualReview(entry, now, "MISSING_REFUND");
            return;
        }
        RefundAttemptResponse response = attempt.get();
        if (PROVIDER_TIMEOUT_STATUS.equals(response.status())) {
            markPendingReconciliation(entry, now, "PROVIDER_TIMEOUT");
            return;
        }
        if (PENDING_STATUS.equals(response.status()) && isBlank(response.providerRefundId())) {
            markManualReview(entry, now, "PENDING_WITHOUT_PROVIDER_REFUND_ID");
            return;
        }
        if (!isRecoverableRefundStatus(response.status())) {
            markManualReview(entry, now, "UNSUPPORTED_REFUND_STATUS_" + response.status());
            return;
        }
        recoveryPort.completeRecovered(
                entry.recordId(),
                HttpStatus.OK.value(),
                serialize(response),
                now
        );
        recordAudit("PAYMENT_IDEMPOTENCY_RECOVERED", entry, "RECOVERED");
    }

    private void markManualReview(
            PaymentIdempotencyRecoveryEntry entry,
            OffsetDateTime now,
            String reason
    ) {
        recoveryPort.markManualReview(entry.recordId(), now);
        recordAudit("PAYMENT_IDEMPOTENCY_MANUAL_REVIEW", entry, reason);
    }

    private void markPendingReconciliation(
            PaymentIdempotencyRecoveryEntry entry,
            OffsetDateTime now,
            String reason
    ) {
        recoveryPort.markPendingReconciliation(entry.recordId(), now);
        recordAudit("PAYMENT_IDEMPOTENCY_PENDING_RECONCILIATION", entry, reason);
    }

    private boolean isRecoverablePaymentStatus(String status) {
        return SUCCEEDED_STATUS.equals(status)
                || FAILED_STATUS.equals(status)
                || PENDING_STATUS.equals(status)
                || REFUNDED_STATUS.equals(status);
    }

    private boolean isRecoverableRefundStatus(String status) {
        return SUCCEEDED_STATUS.equals(status)
                || FAILED_STATUS.equals(status)
                || PENDING_STATUS.equals(status);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void recordAudit(
            String action,
            PaymentIdempotencyRecoveryEntry entry,
            String recoveryStatus
    ) {
        auditEventRecorder.record(new AuditEventCommand(
                null,
                action,
                IDEMPOTENCY_RESOURCE_TYPE,
                Long.toString(entry.recordId()),
                "provider=%s; resourceType=%s; recoveryStatus=%s".formatted(
                        entry.providerCode(),
                        entry.resourceType(),
                        recoveryStatus
                )
        ));
    }

    private String serialize(PaymentAttemptResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payment response could not be serialized", exception);
        }
    }

    private String serialize(RefundAttemptResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Refund response could not be serialized", exception);
        }
    }
}
