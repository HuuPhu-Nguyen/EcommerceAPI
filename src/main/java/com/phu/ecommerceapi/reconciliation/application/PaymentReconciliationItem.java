package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentReconciliationItem(
        UUID id,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerCode,
        String providerPaymentId
) {
}
