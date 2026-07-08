package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID paymentId,
        UUID orderId,
        String provider,
        String status,
        String providerStatus,
        String providerPaymentId,
        String failureCode,
        String message,
        BigDecimal amount,
        String currency
) {

    public PaymentAttemptResponse withProvider(String provider) {
        return new PaymentAttemptResponse(
                paymentId,
                orderId,
                provider,
                status,
                providerStatus,
                providerPaymentId,
                failureCode,
                message,
                amount,
                currency
        );
    }
}
