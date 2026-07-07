package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID paymentId,
        UUID orderId,
        String status,
        String providerStatus,
        String providerPaymentId,
        String failureCode,
        String message,
        BigDecimal amount,
        String currency
) {
}
