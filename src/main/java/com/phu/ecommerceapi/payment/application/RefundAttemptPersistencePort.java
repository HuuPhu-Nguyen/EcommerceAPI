package com.phu.ecommerceapi.payment.application;

import java.util.Optional;
import java.util.UUID;

public interface RefundAttemptPersistencePort {

    void validateRefundable(long customerId, UUID paymentId);

    RefundAttemptSnapshot startAttempt(long customerId, UUID paymentId, String idempotencyKey, String reason);

    RefundAttemptUpdate markSucceeded(UUID refundId, PaymentRefundProviderResult providerResult);

    RefundAttemptUpdate markFailed(UUID refundId, PaymentRefundProviderResult providerResult);

    RefundAttemptUpdate markProviderTimeout(UUID refundId, String message);

    Optional<RefundWebhookAttempt> findForProviderWebhook(
            String providerCode,
            UUID refundId,
            String providerRefundId
    );
}
