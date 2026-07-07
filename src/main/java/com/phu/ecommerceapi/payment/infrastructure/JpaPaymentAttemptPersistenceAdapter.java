package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.application.PaymentAttemptPersistencePort;
import com.phu.ecommerceapi.payment.application.PaymentAttemptSnapshot;
import com.phu.ecommerceapi.payment.application.PaymentAttemptUpdate;
import com.phu.ecommerceapi.payment.application.PaymentAttemptView;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.PaymentWebhookAttempt;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaPaymentAttemptPersistenceAdapter implements PaymentAttemptPersistencePort {

    private final CustomerOrderRepository orderRepository;
    private final PaymentRecordRepository paymentRepository;

    public JpaPaymentAttemptPersistenceAdapter(
            CustomerOrderRepository orderRepository,
            PaymentRecordRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public void validatePayable(long customerId, UUID orderId) {
        CustomerOrderRecord order = orderRepository.findWithCustomerById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        assertOrderOwner(order, customerId);
        assertPayable(order);
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ConflictException("Order already has a payment attempt");
        }
    }

    @Override
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

    @Override
    public PaymentAttemptUpdate markSucceeded(UUID paymentId, PaymentProviderResult providerResult) {
        PaymentRecord payment = findForUpdate(paymentId);
        if (payment.getStatus().isTerminal()) {
            return new PaymentAttemptUpdate(toView(payment), false);
        }
        payment.markSucceeded(providerResult);
        payment.getOrder().markPaid();
        return new PaymentAttemptUpdate(toView(payment), true);
    }

    @Override
    public PaymentAttemptUpdate markFailed(UUID paymentId, PaymentProviderResult providerResult) {
        PaymentRecord payment = findForUpdate(paymentId);
        if (payment.getStatus().isTerminal()) {
            return new PaymentAttemptUpdate(toView(payment), false);
        }
        payment.markFailed(providerResult);
        payment.getOrder().markPaymentFailed();
        return new PaymentAttemptUpdate(toView(payment), true);
    }

    @Override
    public PaymentAttemptUpdate markProviderTimeout(UUID paymentId, String message) {
        PaymentRecord payment = findForUpdate(paymentId);
        if (payment.getStatus().isTerminal()) {
            return new PaymentAttemptUpdate(toView(payment), false);
        }
        payment.markProviderTimeout(message);
        payment.getOrder().markPaymentFailed();
        return new PaymentAttemptUpdate(toView(payment), true);
    }

    @Override
    public Optional<PaymentWebhookAttempt> findForProviderWebhook(UUID paymentId, String providerPaymentId) {
        Optional<PaymentRecord> payment;
        if (paymentId != null) {
            payment = paymentRepository.findById(paymentId);
        } else if (providerPaymentId != null) {
            payment = paymentRepository.findByProviderPaymentId(providerPaymentId);
        } else {
            payment = Optional.empty();
        }
        return payment.map(record -> new PaymentWebhookAttempt(record.getId(), record.getStatus()));
    }

    private PaymentRecord findForUpdate(UUID paymentId) {
        return paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
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

    private PaymentAttemptView toView(PaymentRecord payment) {
        return new PaymentAttemptView(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProviderStatus(),
                payment.getProviderPaymentId(),
                payment.getFailureCode(),
                payment.getProviderMessage()
        );
    }
}
