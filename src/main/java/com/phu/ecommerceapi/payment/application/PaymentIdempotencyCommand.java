package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.shared.domain.IdempotencyKey;

public record PaymentIdempotencyCommand(
        long customerId,
        String endpoint,
        String operation,
        String idempotencyKey,
        String requestBody
) {

    public PaymentIdempotencyCommand {
        if (customerId <= 0) {
            throw new IllegalArgumentException("Customer id must be positive");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Idempotency endpoint is required");
        }
        endpoint = endpoint.trim();

        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Idempotency operation is required");
        }
        operation = operation.trim();

        idempotencyKey = IdempotencyKey.of(idempotencyKey).value();
        requestBody = requestBody == null ? "" : requestBody;
    }
}
