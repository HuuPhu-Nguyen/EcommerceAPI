package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingCommand;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingPort;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentAttemptService {

    private static final String PAYMENT_RESOURCE_TYPE = "PAYMENT";

    private final PaymentAttemptPersistencePort paymentAttempts;
    private final PaymentLedgerPostingPort ledgerPostingPort;
    private final AuditEventRecorder auditEventRecorder;
    private final BusinessMetrics businessMetrics;

    public PaymentAttemptService(
            PaymentAttemptPersistencePort paymentAttempts,
            PaymentLedgerPostingPort ledgerPostingPort,
            AuditEventRecorder auditEventRecorder,
            BusinessMetrics businessMetrics
    ) {
        this.paymentAttempts = paymentAttempts;
        this.ledgerPostingPort = ledgerPostingPort;
        this.auditEventRecorder = auditEventRecorder;
        this.businessMetrics = businessMetrics;
    }

    @Transactional(readOnly = true)
    public PaymentPayableOrder validatePayable(long customerId, UUID orderId, String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("payment provider code is required");
        }
        return paymentAttempts.validatePayable(customerId, orderId, providerCode);
    }

    @Transactional
    public PaymentAttemptSnapshot startAttempt(
            long customerId,
            UUID orderId,
            String idempotencyKey,
            String providerCode,
            String providerIdempotencyKey
    ) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("payment provider code is required");
        }
        if (providerIdempotencyKey == null || providerIdempotencyKey.isBlank()) {
            throw new IllegalArgumentException("payment provider idempotency key is required");
        }
        return paymentAttempts.startAttempt(
                customerId,
                orderId,
                idempotencyKey,
                providerCode,
                providerIdempotencyKey
        );
    }

    @Transactional
    public PaymentAttemptResponse completeAttempt(
            UUID paymentId,
            PaymentProviderResult providerResult,
            CurrentUser actor
    ) {
        PaymentAttemptUpdate update;
        if (isSuccessful(providerResult)) {
            update = paymentAttempts.markSucceeded(paymentId, providerResult);
            if (!update.transitioned()) {
                return toResponse(update.attempt());
            }
            PaymentAttemptView payment = update.attempt();
            ledgerPostingPort.postPaymentSucceeded(new PaymentLedgerPostingCommand(
                    payment.paymentId(),
                    payment.orderId(),
                    payment.customerId(),
                    payment.amount(),
                    payment.currency(),
                    payment.providerPaymentId()
            ));
            recordAudit(actor, "PAYMENT_SUCCEEDED", payment);
        } else if (providerResult.status() == PaymentProviderStatus.PENDING) {
            update = paymentAttempts.markPending(paymentId, providerResult);
            if (!update.transitioned()) {
                return toResponse(update.attempt());
            }
            recordAudit(actor, "PAYMENT_PENDING", update.attempt());
        } else {
            update = paymentAttempts.markFailed(paymentId, providerResult);
            if (!update.transitioned()) {
                return toResponse(update.attempt());
            }
            recordAudit(actor, "PAYMENT_FAILED", update.attempt());
        }

        businessMetrics.paymentOutcome(update.attempt().status().name());
        return toResponse(update.attempt());
    }

    @Transactional
    public PaymentAttemptResponse markProviderTimeout(UUID paymentId, String message, CurrentUser actor) {
        PaymentAttemptUpdate update = paymentAttempts.markProviderTimeout(paymentId, message);
        if (update.transitioned()) {
            recordAudit(actor, "PAYMENT_PROVIDER_TIMEOUT", update.attempt());
        }
        businessMetrics.paymentOutcome(update.attempt().status().name());
        return toResponse(update.attempt());
    }

    @Transactional(readOnly = true)
    public Optional<PaymentAttemptResponse> findAttemptResponse(UUID paymentId) {
        return paymentAttempts.findAttempt(paymentId)
                .map(this::toResponse);
    }

    private boolean isSuccessful(PaymentProviderResult providerResult) {
        return providerResult.status() == PaymentProviderStatus.SUCCEEDED
                || providerResult.status() == PaymentProviderStatus.DUPLICATE;
    }

    private void recordAudit(CurrentUser actor, String action, PaymentAttemptView payment) {
        auditEventRecorder.record(new AuditEventCommand(
                actor == null ? null : actor.subject(),
                action,
                PAYMENT_RESOURCE_TYPE,
                payment.paymentId().toString(),
                "provider=%s; orderId=%s; status=%s; amount=%s %s".formatted(
                        payment.providerCode(),
                        payment.orderId(),
                        payment.status(),
                        payment.amount(),
                        payment.currency()
                )
        ));
    }

    private PaymentAttemptResponse toResponse(PaymentAttemptView payment) {
        return new PaymentAttemptResponse(
                payment.paymentId(),
                payment.orderId(),
                payment.providerCode(),
                payment.status().name(),
                payment.providerStatus(),
                payment.providerPaymentId(),
                payment.failureCode(),
                payment.providerMessage(),
                payment.amount(),
                payment.currency()
        );
    }
}
