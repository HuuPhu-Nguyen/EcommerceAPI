package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAttemptSnapshot(
        UUID paymentId,
        UUID orderId,
        String providerCode,
        String providerIdempotencyKey,
        BigDecimal amount,
        String currency
) {
}
