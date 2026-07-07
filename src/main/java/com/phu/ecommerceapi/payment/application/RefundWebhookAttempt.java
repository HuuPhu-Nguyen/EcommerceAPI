package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.RefundStatus;

import java.util.UUID;

public record RefundWebhookAttempt(
        UUID refundId,
        RefundStatus status
) {
}
