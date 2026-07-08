package com.phu.ecommerceapi.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        String provider,
        String status,
        String providerStatus,
        String providerRefundId,
        String failureCode,
        String message,
        BigDecimal amount,
        String currency
) {
}
