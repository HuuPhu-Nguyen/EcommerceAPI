package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingPort;
import com.phu.ecommerceapi.ledger.application.RefundLedgerPostingCommand;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RefundAttemptService {

    private static final String REFUND_RESOURCE_TYPE = "REFUND";

    private final RefundAttemptPersistencePort refundAttempts;
    private final PaymentLedgerPostingPort ledgerPostingPort;
    private final AuditEventRecorder auditEventRecorder;
    private final BusinessMetrics businessMetrics;

    public RefundAttemptService(
            RefundAttemptPersistencePort refundAttempts,
            PaymentLedgerPostingPort ledgerPostingPort,
            AuditEventRecorder auditEventRecorder,
            BusinessMetrics businessMetrics
    ) {
        this.refundAttempts = refundAttempts;
        this.ledgerPostingPort = ledgerPostingPort;
        this.auditEventRecorder = auditEventRecorder;
        this.businessMetrics = businessMetrics;
    }

    @Transactional(readOnly = true)
    public RefundablePayment validateRefundable(long customerId, UUID paymentId) {
        return refundAttempts.validateRefundable(customerId, paymentId);
    }

    @Transactional
    public RefundAttemptSnapshot startAttempt(
            long customerId,
            UUID paymentId,
            String idempotencyKey,
            String reason
    ) {
        return refundAttempts.startAttempt(customerId, paymentId, idempotencyKey, reason);
    }

    @Transactional
    public RefundAttemptResponse completeAttempt(
            UUID refundId,
            PaymentRefundProviderResult providerResult,
            CurrentUser actor
    ) {
        RefundAttemptUpdate update;
        if (isSuccessful(providerResult)) {
            update = refundAttempts.markSucceeded(refundId, providerResult);
            if (!update.transitioned()) {
                return toResponse(update.attempt());
            }
            RefundAttemptView refund = update.attempt();
            ledgerPostingPort.postRefundSucceeded(new RefundLedgerPostingCommand(
                    refund.refundId(),
                    refund.paymentId(),
                    refund.orderId(),
                    refund.customerId(),
                    refund.amount(),
                    refund.currency(),
                    refund.providerCode(),
                    refund.providerRefundId()
            ));
            recordAudit(actor, "REFUND_SUCCEEDED", refund);
        } else if (providerResult.status() == PaymentProviderStatus.PENDING) {
            update = refundAttempts.markPending(refundId, providerResult);
            if (!update.transitioned()) {
                return toResponse(update.attempt());
            }
            recordAudit(actor, "REFUND_PENDING", update.attempt());
        } else {
            update = refundAttempts.markFailed(refundId, providerResult);
            if (!update.transitioned()) {
                return toResponse(update.attempt());
            }
            recordAudit(actor, "REFUND_FAILED", update.attempt());
        }

        businessMetrics.refundOutcome(update.attempt().status().name());
        return toResponse(update.attempt());
    }

    @Transactional
    public RefundAttemptResponse markProviderTimeout(UUID refundId, String message, CurrentUser actor) {
        RefundAttemptUpdate update = refundAttempts.markProviderTimeout(refundId, message);
        if (update.transitioned()) {
            recordAudit(actor, "REFUND_PROVIDER_TIMEOUT", update.attempt());
        }
        businessMetrics.refundOutcome(update.attempt().status().name());
        return toResponse(update.attempt());
    }

    @Transactional
    public void recordProviderUnavailable(
            CurrentUser actor,
            UUID paymentId,
            String providerCode,
            String message
    ) {
        auditEventRecorder.record(new AuditEventCommand(
                actor == null ? null : actor.subject(),
                "REFUND_PROVIDER_UNAVAILABLE",
                "PAYMENT",
                paymentId.toString(),
                "provider=%s; paymentId=%s; status=UNAVAILABLE; message=%s".formatted(
                        providerCode,
                        paymentId,
                        message
                )
        ));
        businessMetrics.refundOutcome("PROVIDER_UNAVAILABLE");
    }

    private boolean isSuccessful(PaymentRefundProviderResult providerResult) {
        return providerResult.status() == PaymentProviderStatus.SUCCEEDED
                || providerResult.status() == PaymentProviderStatus.DUPLICATE;
    }

    private void recordAudit(CurrentUser actor, String action, RefundAttemptView refund) {
        auditEventRecorder.record(new AuditEventCommand(
                actor == null ? null : actor.subject(),
                action,
                REFUND_RESOURCE_TYPE,
                refund.refundId().toString(),
                "provider=%s; paymentId=%s; orderId=%s; status=%s; amount=%s %s".formatted(
                        refund.providerCode(),
                        refund.paymentId(),
                        refund.orderId(),
                        refund.status(),
                        refund.amount(),
                        refund.currency()
                )
        ));
    }

    private RefundAttemptResponse toResponse(RefundAttemptView refund) {
        return new RefundAttemptResponse(
                refund.refundId(),
                refund.paymentId(),
                refund.orderId(),
                refund.providerCode(),
                refund.status().name(),
                refund.providerStatus(),
                refund.providerRefundId(),
                refund.failureCode(),
                refund.providerMessage(),
                refund.amount(),
                refund.currency()
        );
    }
}
