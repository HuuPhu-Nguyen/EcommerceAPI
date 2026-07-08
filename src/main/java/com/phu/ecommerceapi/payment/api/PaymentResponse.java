package com.phu.ecommerceapi.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
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
}
