package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAttemptView(
        UUID paymentId,
        UUID orderId,
        long customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerStatus,
        String providerPaymentId,
        String failureCode,
        String providerMessage
) {
}
