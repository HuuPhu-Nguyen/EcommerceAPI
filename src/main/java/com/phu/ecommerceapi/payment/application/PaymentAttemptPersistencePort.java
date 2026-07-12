package com.phu.ecommerceapi.payment.application;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptPersistencePort {

    PaymentPayableOrder validatePayable(long customerId, UUID orderId, String providerCode);

    PaymentAttemptSnapshot startAttempt(
            long customerId,
            UUID orderId,
            String idempotencyKey,
            String providerCode,
            String providerIdempotencyKey
    );

    PaymentAttemptUpdate recordProviderSucceeded(UUID paymentId, PaymentProviderResult providerResult);

    PaymentAttemptUpdate finalizeProviderSucceededPayment(UUID paymentId);

    PaymentAttemptUpdate markFailed(UUID paymentId, PaymentProviderResult providerResult);

    PaymentAttemptUpdate markPending(UUID paymentId, PaymentProviderResult providerResult);

    PaymentAttemptUpdate markProviderTimeout(UUID paymentId, String message);

    Optional<PaymentAttemptView> findAttempt(UUID paymentId);

    Optional<PaymentWebhookAttempt> findForProviderWebhook(
            String providerCode,
            UUID paymentId,
            String providerPaymentId
    );
}
