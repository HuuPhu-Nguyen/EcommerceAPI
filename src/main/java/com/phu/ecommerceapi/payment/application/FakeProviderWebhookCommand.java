package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;

import java.util.UUID;

public record FakeProviderWebhookCommand(
        String webhookSecret,
        String requestBody,
        String eventId,
        ProviderWebhookEventType eventType,
        UUID paymentId,
        UUID refundId,
        String providerPaymentId,
        String providerRefundId,
        String failureCode,
        String message
) {

    public FakeProviderWebhookCommand {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("provider webhook request body is required");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("provider event id is required");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("provider webhook event type is required");
        }
        eventId = eventId.trim();
        providerPaymentId = trimToNull(providerPaymentId);
        providerRefundId = trimToNull(providerRefundId);
        failureCode = trimToNull(failureCode);
        message = trimToNull(message);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
