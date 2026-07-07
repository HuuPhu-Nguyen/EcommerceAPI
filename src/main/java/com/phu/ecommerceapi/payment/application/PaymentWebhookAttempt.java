package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.PaymentStatus;

import java.util.UUID;

public record PaymentWebhookAttempt(
        UUID paymentId,
        PaymentStatus status
) {
}
