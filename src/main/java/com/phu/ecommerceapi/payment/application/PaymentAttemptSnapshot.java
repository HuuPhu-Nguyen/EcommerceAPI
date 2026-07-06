package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAttemptSnapshot(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency
) {
}
