package com.phu.ecommerceapi.payment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProviderOutcomePersistenceService {

    private final PaymentAttemptPersistencePort paymentAttempts;
    private final RefundAttemptPersistencePort refundAttempts;

    public ProviderOutcomePersistenceService(
            PaymentAttemptPersistencePort paymentAttempts,
            RefundAttemptPersistencePort refundAttempts
    ) {
        this.paymentAttempts = paymentAttempts;
        this.refundAttempts = refundAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAttemptUpdate recordProviderSucceeded(
            UUID paymentId,
            PaymentProviderResult providerResult
    ) {
        return paymentAttempts.recordProviderSucceeded(paymentId, providerResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundAttemptUpdate recordProviderRefundSucceeded(
            UUID refundId,
            PaymentRefundProviderResult providerResult
    ) {
        return refundAttempts.recordProviderRefundSucceeded(refundId, providerResult);
    }
}
