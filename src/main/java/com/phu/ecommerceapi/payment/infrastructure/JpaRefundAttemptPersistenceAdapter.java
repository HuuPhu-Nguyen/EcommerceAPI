package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderResult;
import com.phu.ecommerceapi.payment.application.RefundAttemptPersistencePort;
import com.phu.ecommerceapi.payment.application.RefundAttemptSnapshot;
import com.phu.ecommerceapi.payment.application.RefundAttemptUpdate;
import com.phu.ecommerceapi.payment.application.RefundAttemptView;
import com.phu.ecommerceapi.payment.application.RefundWebhookAttempt;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaRefundAttemptPersistenceAdapter implements RefundAttemptPersistencePort {

    private final PaymentRecordRepository paymentRepository;
    private final RefundRecordRepository refundRepository;

    public JpaRefundAttemptPersistenceAdapter(
            PaymentRecordRepository paymentRepository,
            RefundRecordRepository refundRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @Override
    public void validateRefundable(long customerId, UUID paymentId) {
        PaymentRecord payment = paymentRepository.findWithOrderById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        assertRefundable(payment, customerId);
    }

    @Override
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

    @Override
    public RefundAttemptUpdate markSucceeded(UUID refundId, PaymentRefundProviderResult providerResult) {
        RefundRecord refund = findForUpdate(refundId);
        if (refund.getStatus().isTerminal()) {
            return new RefundAttemptUpdate(toView(refund), false);
        }
        refund.markSucceeded(providerResult);
        refund.getPayment().markRefunded();
        refund.getPayment().getOrder().refund();
        return new RefundAttemptUpdate(toView(refund), true);
    }

    @Override
    public RefundAttemptUpdate markFailed(UUID refundId, PaymentRefundProviderResult providerResult) {
        RefundRecord refund = findForUpdate(refundId);
        if (refund.getStatus().isTerminal()) {
            return new RefundAttemptUpdate(toView(refund), false);
        }
        refund.markFailed(providerResult);
        return new RefundAttemptUpdate(toView(refund), true);
    }

    @Override
    public RefundAttemptUpdate markProviderTimeout(UUID refundId, String message) {
        RefundRecord refund = findForUpdate(refundId);
        if (refund.getStatus().isTerminal()) {
            return new RefundAttemptUpdate(toView(refund), false);
        }
        refund.markProviderTimeout(message);
        return new RefundAttemptUpdate(toView(refund), true);
    }

    @Override
    public Optional<RefundWebhookAttempt> findForProviderWebhook(UUID refundId, String providerRefundId) {
        Optional<RefundRecord> refund;
        if (refundId != null) {
            refund = refundRepository.findById(refundId);
        } else if (providerRefundId != null) {
            refund = refundRepository.findByProviderRefundId(providerRefundId);
        } else {
            refund = Optional.empty();
        }
        return refund.map(record -> new RefundWebhookAttempt(record.getId(), record.getStatus()));
    }

    private RefundRecord findForUpdate(UUID refundId) {
        return refundRepository.findForUpdateById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund not found"));
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

    private RefundAttemptView toView(RefundRecord refund) {
        return new RefundAttemptView(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getOrderId(),
                refund.getCustomerId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getProviderStatus(),
                refund.getProviderRefundId(),
                refund.getFailureCode(),
                refund.getProviderMessage()
        );
    }
}
