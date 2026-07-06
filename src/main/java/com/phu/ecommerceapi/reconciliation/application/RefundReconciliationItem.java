package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.payment.domain.RefundStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundReconciliationItem(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status
) {
}
