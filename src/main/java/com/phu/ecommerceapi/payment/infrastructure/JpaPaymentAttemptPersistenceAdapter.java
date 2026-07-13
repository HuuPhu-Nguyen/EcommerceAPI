package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.application.PaymentAttemptPersistencePort;
import com.phu.ecommerceapi.payment.application.PaymentAttemptSnapshot;
import com.phu.ecommerceapi.payment.application.PaymentAttemptUpdate;
import com.phu.ecommerceapi.payment.application.PaymentAttemptView;
import com.phu.ecommerceapi.payment.application.PaymentPayableOrder;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.PaymentWebhookAttempt;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class JpaPaymentAttemptPersistenceAdapter implements PaymentAttemptPersistencePort {

    private static final Set<PaymentStatus> SUCCESSFUL_ORDER_BLOCKING_STATUSES = Set.of(
            PaymentStatus.PROVIDER_SUCCEEDED_LEDGER_PENDING,
            PaymentStatus.SUCCEEDED,
            PaymentStatus.REFUNDED
    );

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
    public PaymentPayableOrder validatePayable(long customerId, UUID orderId, String providerCode) {
        normalizeProviderCode(providerCode);
        CustomerOrderRecord order = orderRepository.findWithCustomerById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        assertOrderOwner(order, customerId);
        assertPayable(order);
        assertNoBlockingPaymentAttempt(orderId);
        return new PaymentPayableOrder(order.getId(), order.getTotalAmount(), order.getCurrency());
    }

    @Override
    public PaymentAttemptSnapshot startAttempt(
            long customerId,
            UUID orderId,
            String idempotencyKey,
            String providerCode,
            String providerIdempotencyKey
    ) {
        CustomerOrderRecord order = orderRepository.findForPaymentById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        assertOrderOwner(order, customerId);
        assertPayable(order);

        assertNoBlockingPaymentAttempt(orderId);

        PaymentRecord payment = PaymentRecord.pending(order, idempotencyKey, providerCode, providerIdempotencyKey);
        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Order already has an active or successful payment attempt");
        }

        return new PaymentAttemptSnapshot(
                payment.getId(),
                order.getId(),
                payment.getProviderCode(),
                payment.getProviderIdempotencyKey(),
                order.getTotalAmount(),
                order.getCurrency()
        );
    }

    @Override
    public PaymentAttemptUpdate recordProviderSucceeded(UUID paymentId, PaymentProviderResult providerResult) {
        PaymentRecord payment = findForUpdate(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        payment.recordProviderSucceeded(providerResult);
        return new PaymentAttemptUpdate(toView(payment), payment.getStatus() != previousStatus);
    }

    @Override
    public PaymentAttemptUpdate finalizeProviderSucceededPayment(UUID paymentId) {
        PaymentRecord payment = findForUpdate(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        payment.finalizeProviderSucceeded();
        boolean transitioned = payment.getStatus() != previousStatus;
        if (transitioned) {
            payment.getOrder().markPaid();
        }
        return new PaymentAttemptUpdate(toView(payment), transitioned);
    }

    @Override
    public PaymentAttemptUpdate markFailed(UUID paymentId, PaymentProviderResult providerResult) {
        PaymentRecord payment = findForUpdate(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        payment.markFailed(providerResult);
        return new PaymentAttemptUpdate(toView(payment), payment.getStatus() != previousStatus);
    }

    @Override
    public PaymentAttemptUpdate markPending(UUID paymentId, PaymentProviderResult providerResult) {
        PaymentRecord payment = findForUpdate(paymentId);
        boolean updated = payment.markPending(providerResult);
        return new PaymentAttemptUpdate(toView(payment), updated);
    }

    @Override
    public PaymentAttemptUpdate markProviderTimeout(UUID paymentId, String message) {
        PaymentRecord payment = findForUpdate(paymentId);
        if (payment.getStatus().isTerminal()) {
            return new PaymentAttemptUpdate(toView(payment), false);
        }
        payment.markProviderTimeout(message);
        return new PaymentAttemptUpdate(toView(payment), true);
    }

    @Override
    public Optional<PaymentAttemptView> findAttempt(UUID paymentId) {
        return paymentRepository.findWithOrderById(paymentId)
                .map(this::toView);
    }

    @Override
    public Optional<PaymentWebhookAttempt> findForProviderWebhook(
            String providerCode,
            UUID paymentId,
            String providerPaymentId
    ) {
        String normalizedProviderCode = normalizeProviderCode(providerCode);
        if (paymentId != null) {
            return paymentRepository.findWebhookAttemptByIdAndProvider(
                    paymentId,
                    normalizedProviderCode,
                    providerPaymentId
            );
        }
        if (providerPaymentId != null) {
            return paymentRepository.findWebhookAttemptByProviderReference(
                    normalizedProviderCode,
                    providerPaymentId
            );
        }
        return Optional.empty();
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

    private void assertNoBlockingPaymentAttempt(UUID orderId) {
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
            throw new ConflictException("Order already has a payment attempt in progress");
        }
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PROVIDER_TIMEOUT)) {
            throw new ConflictException("Order has a payment attempt with unknown provider outcome");
        }
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, SUCCESSFUL_ORDER_BLOCKING_STATUSES)) {
            throw new ConflictException("Order already has a successful payment");
        }
    }

    private PaymentAttemptView toView(PaymentRecord payment) {
        return new PaymentAttemptView(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getCustomerId(),
                payment.getProviderCode(),
                payment.getProviderIdempotencyKey(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProviderStatus(),
                payment.getProviderPaymentId(),
                payment.getFailureCode(),
                payment.getProviderMessage()
        );
    }

    private String normalizeProviderCode(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("payment provider code is required");
        }
        return providerCode.trim().toLowerCase(Locale.ROOT);
    }
}
