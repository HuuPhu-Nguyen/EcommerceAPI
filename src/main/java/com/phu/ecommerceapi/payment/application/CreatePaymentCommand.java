package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.identity.application.CurrentUser;

import java.util.UUID;

public record CreatePaymentCommand(
        CurrentUser currentUser,
        String idempotencyKey,
        String requestBody,
        UUID orderId,
        String paymentMethodToken
) {

    public CreatePaymentCommand {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("payment request body is required");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("order id is required");
        }
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("payment method token is required");
        }
        paymentMethodToken = paymentMethodToken.trim();
    }
}
