package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;

import java.util.Locale;
import java.util.UUID;

public record ProviderWebhookCommand(
        String providerCode,
        String webhookSecret,
        String requestBody,
        String providerEventId,
        ProviderWebhookEventType eventType,
        UUID paymentId,
        UUID refundId,
        String providerPaymentId,
        String providerRefundId,
        String failureCode,
        String message
) {

    public ProviderWebhookCommand {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("provider code is required");
        }
        providerCode = providerCode.trim().toLowerCase(Locale.ROOT);
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("provider webhook request body is required");
        }
        if (providerEventId == null || providerEventId.isBlank()) {
            throw new IllegalArgumentException("provider event id is required");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("provider webhook event type is required");
        }
        providerEventId = providerEventId.trim();
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
