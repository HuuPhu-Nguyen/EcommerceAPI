package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingCommand;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingPort;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.api.PaymentResponse;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentAttemptService {

    private static final String PAYMENT_RESOURCE_TYPE = "PAYMENT";

    private final CustomerOrderRepository orderRepository;
    private final PaymentRecordRepository paymentRepository;
    private final PaymentLedgerPostingPort ledgerPostingPort;
    private final AuditEventRecorder auditEventRecorder;

    public PaymentAttemptService(
            CustomerOrderRepository orderRepository,
            PaymentRecordRepository paymentRepository,
            PaymentLedgerPostingPort ledgerPostingPort,
            AuditEventRecorder auditEventRecorder
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerPostingPort = ledgerPostingPort;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional(readOnly = true)
    public void validatePayable(long customerId, UUID orderId) {
        CustomerOrderRecord order = orderRepository.findWithCustomerById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        assertOrderOwner(order, customerId);
        assertPayable(order);
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ConflictException("Order already has a payment attempt");
        }
    }

    @Transactional
    public PaymentAttemptSnapshot startAttempt(long customerId, UUID orderId, String idempotencyKey) {
        CustomerOrderRecord order = orderRepository.findForPaymentById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        assertOrderOwner(order, customerId);
        assertPayable(order);

        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ConflictException("Order already has a payment attempt");
        }

        PaymentRecord payment = PaymentRecord.pending(order, idempotencyKey);
        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Order already has a payment attempt");
        }

        return new PaymentAttemptSnapshot(
                payment.getId(),
                order.getId(),
                order.getTotalAmount(),
                order.getCurrency()
        );
    }

    @Transactional
    public PaymentResponse completeAttempt(
            UUID paymentId,
            PaymentProviderResult providerResult,
            CurrentUser actor
    ) {
        PaymentRecord payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (payment.getStatus().isTerminal()) {
            return toResponse(payment);
        }

        if (isSuccessful(providerResult)) {
            payment.markSucceeded(providerResult);
            payment.getOrder().markPaid();
            ledgerPostingPort.postPaymentSucceeded(new PaymentLedgerPostingCommand(
                    payment.getId(),
                    payment.getOrder().getId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getProviderPaymentId()
            ));
            recordAudit(actor, "PAYMENT_SUCCEEDED", payment);
        } else {
            payment.markFailed(providerResult);
            payment.getOrder().markPaymentFailed();
            recordAudit(actor, "PAYMENT_FAILED", payment);
        }

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse markProviderTimeout(UUID paymentId, String message, CurrentUser actor) {
        PaymentRecord payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (!payment.getStatus().isTerminal()) {
            payment.markProviderTimeout(message);
            payment.getOrder().markPaymentFailed();
            recordAudit(actor, "PAYMENT_PROVIDER_TIMEOUT", payment);
        }
        return toResponse(payment);
    }

    private boolean isSuccessful(PaymentProviderResult providerResult) {
        return providerResult.status() == PaymentProviderStatus.SUCCEEDED
                || providerResult.status() == PaymentProviderStatus.DUPLICATE;
    }

    private void assertOrderOwner(CustomerOrderRecord order, long customerId) {
        if (order.getCustomer() == null || order.getCustomer().getId() != customerId) {
            throw new AccessDeniedException("Order does not belong to current user");
        }
    }

    private void assertPayable(CustomerOrderRecord order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Order is not payable");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Order total must be positive");
        }
    }

    private void recordAudit(CurrentUser actor, String action, PaymentRecord payment) {
        auditEventRecorder.record(new AuditEventCommand(
                actor == null ? null : actor.subject(),
                action,
                PAYMENT_RESOURCE_TYPE,
                payment.getId().toString(),
                "orderId=%s; status=%s; amount=%s %s".formatted(
                        payment.getOrder().getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getCurrency()
                )
        ));
    }

    private PaymentResponse toResponse(PaymentRecord payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getProviderStatus(),
                payment.getProviderPaymentId(),
                payment.getFailureCode(),
                payment.getProviderMessage(),
                payment.getAmount(),
                payment.getCurrency()
        );
    }
}
