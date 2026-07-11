package com.phu.ecommerceapi.payment.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentIdempotencyRecoveryPort {

    List<PaymentIdempotencyRecoveryEntry> claimExpired(OffsetDateTime now, int limit);

    void completeRecovered(long recordId, int responseStatus, String responseBody, OffsetDateTime recoveredAt);

    void markPendingReconciliation(long recordId, OffsetDateTime attemptedAt);

    void markManualReview(long recordId, OffsetDateTime attemptedAt);
}
