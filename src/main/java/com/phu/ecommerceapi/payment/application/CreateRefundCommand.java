package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.identity.application.CurrentUser;

import java.util.UUID;

public record CreateRefundCommand(
        UUID paymentId,
        CurrentUser currentUser,
        String idempotencyKey,
        String requestBody,
        String reason
) {

    public CreateRefundCommand {
        if (paymentId == null) {
            throw new IllegalArgumentException("payment id is required");
        }
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("refund request body is required");
        }
        if (reason == null || reason.isBlank()) {
            reason = "customer_request";
        } else {
            reason = reason.trim();
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("refund reason must be 500 characters or fewer");
        }
    }
}
