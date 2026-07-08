package com.phu.ecommerceapi.payment.application;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptPersistencePort {

    PaymentPayableOrder validatePayable(long customerId, UUID orderId);

    PaymentAttemptSnapshot startAttempt(long customerId, UUID orderId, String idempotencyKey);

    PaymentAttemptUpdate markSucceeded(UUID paymentId, PaymentProviderResult providerResult);

    PaymentAttemptUpdate markFailed(UUID paymentId, PaymentProviderResult providerResult);

    PaymentAttemptUpdate markProviderTimeout(UUID paymentId, String message);

    Optional<PaymentWebhookAttempt> findForProviderWebhook(UUID paymentId, String providerPaymentId);
}
