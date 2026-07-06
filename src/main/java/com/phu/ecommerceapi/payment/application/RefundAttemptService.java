package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingPort;
import com.phu.ecommerceapi.ledger.application.RefundLedgerPostingCommand;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.payment.api.RefundResponse;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecord;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class RefundAttemptService {

    private static final String REFUND_RESOURCE_TYPE = "REFUND";

    private final PaymentRecordRepository paymentRepository;
    private final RefundRecordRepository refundRepository;
    private final PaymentLedgerPostingPort ledgerPostingPort;
    private final AuditEventRecorder auditEventRecorder;
    private final BusinessMetrics businessMetrics;

    public RefundAttemptService(
            PaymentRecordRepository paymentRepository,
            RefundRecordRepository refundRepository,
            PaymentLedgerPostingPort ledgerPostingPort,
            AuditEventRecorder auditEventRecorder,
            BusinessMetrics businessMetrics
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.ledgerPostingPort = ledgerPostingPort;
        this.auditEventRecorder = auditEventRecorder;
        this.businessMetrics = businessMetrics;
    }

    @Transactional(readOnly = true)
    public void validateRefundable(long customerId, UUID paymentId) {
        PaymentRecord payment = paymentRepository.findWithOrderById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        assertRefundable(payment, customerId);
    }

    @Transactional
    public RefundAttemptSnapshot startAttempt(
            long customerId,
            UUID paymentId,
            String idempotencyKey,
            String reason
    ) {
        PaymentRecord payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        assertRefundable(payment, customerId);

        RefundRecord refund = RefundRecord.pending(payment, idempotencyKey, reason);
        try {
            refund = refundRepository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Payment already has a refund");
        }

        return new RefundAttemptSnapshot(
                refund.getId(),
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProviderPaymentId(),
                payment.getAmount(),
                payment.getCurrency()
        );
    }

    @Transactional
    public RefundResponse completeAttempt(
            UUID refundId,
            PaymentRefundProviderResult providerResult,
            CurrentUser actor
    ) {
        RefundRecord refund = refundRepository.findForUpdateById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund not found"));
        if (refund.getStatus().isTerminal()) {
            return toResponse(refund);
        }

        if (isSuccessful(providerResult)) {
            refund.markSucceeded(providerResult);
            refund.getPayment().markRefunded();
            refund.getPayment().getOrder().refund();
            ledgerPostingPort.postRefundSucceeded(new RefundLedgerPostingCommand(
                    refund.getId(),
                    refund.getPayment().getId(),
                    refund.getOrderId(),
                    refund.getCustomerId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getProviderRefundId()
            ));
            recordAudit(actor, "REFUND_SUCCEEDED", refund);
        } else {
            refund.markFailed(providerResult);
            recordAudit(actor, "REFUND_FAILED", refund);
        }

        businessMetrics.refundOutcome(refund.getStatus().name());
        return toResponse(refund);
    }

    @Transactional
    public RefundResponse markProviderTimeout(UUID refundId, String message, CurrentUser actor) {
        RefundRecord refund = refundRepository.findForUpdateById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund not found"));
        if (!refund.getStatus().isTerminal()) {
            refund.markProviderTimeout(message);
            recordAudit(actor, "REFUND_PROVIDER_TIMEOUT", refund);
        }
        businessMetrics.refundOutcome(refund.getStatus().name());
        return toResponse(refund);
    }

    private boolean isSuccessful(PaymentRefundProviderResult providerResult) {
        return providerResult.status() == PaymentProviderStatus.SUCCEEDED
                || providerResult.status() == PaymentProviderStatus.DUPLICATE;
    }

    private void assertRefundable(PaymentRecord payment, long customerId) {
        if (payment.getCustomerId() != customerId) {
            throw new AccessDeniedException("Payment does not belong to current user");
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ConflictException("Payment is not refundable");
        }
        if (payment.getOrder().getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Order is not refundable");
        }
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            throw new ConflictException("Payment is missing provider confirmation");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Refund amount must be positive");
        }
        if (refundRepository.existsByPaymentId(payment.getId())) {
            throw new ConflictException("Payment already has a refund");
        }
    }

    private void recordAudit(CurrentUser actor, String action, RefundRecord refund) {
        auditEventRecorder.record(new AuditEventCommand(
                actor == null ? null : actor.subject(),
                action,
                REFUND_RESOURCE_TYPE,
                refund.getId().toString(),
                "paymentId=%s; orderId=%s; status=%s; amount=%s %s".formatted(
                        refund.getPayment().getId(),
                        refund.getOrderId(),
                        refund.getStatus(),
                        refund.getAmount(),
                        refund.getCurrency()
                )
        ));
    }

    private RefundResponse toResponse(RefundRecord refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getOrderId(),
                refund.getStatus().name(),
                refund.getProviderStatus(),
                refund.getProviderRefundId(),
                refund.getFailureCode(),
                refund.getProviderMessage(),
                refund.getAmount(),
                refund.getCurrency()
        );
    }
}
