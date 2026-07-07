package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.RefundStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundAttemptView(
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        long customerId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        String providerStatus,
        String providerRefundId,
        String failureCode,
        String providerMessage
) {
}
