package com.phu.ecommerceapi.payment.api;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;

import java.util.UUID;

public record FakeProviderWebhookRequest(
        String eventId,
        String type,
        UUID paymentId,
        UUID refundId,
        String providerPaymentId,
        String providerRefundId,
        String failureCode,
        String message
) {

    public FakeProviderWebhookRequest {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("provider event id is required");
        }
        eventId = eventId.trim();
        ProviderWebhookEventType.fromWireName(type);
        type = type.trim();
        providerPaymentId = trimToNull(providerPaymentId);
        providerRefundId = trimToNull(providerRefundId);
        failureCode = trimToNull(failureCode);
        message = trimToNull(message);
    }

    public ProviderWebhookEventType eventType() {
        return ProviderWebhookEventType.fromWireName(type);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
