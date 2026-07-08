package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundAttemptSnapshot(
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        String providerCode,
        String providerIdempotencyKey,
        String providerPaymentId,
        BigDecimal amount,
        String currency
) {
}
