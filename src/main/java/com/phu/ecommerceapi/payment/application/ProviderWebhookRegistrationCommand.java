package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

public record ProviderWebhookRegistrationCommand(
        String providerCode,
        String providerEventId,
        ProviderWebhookEventType eventType,
        String payloadHash,
        String payload,
        OffsetDateTime receivedAt
) {

    public ProviderWebhookRegistrationCommand {
        providerCode = requireText(providerCode, "provider code").toLowerCase(Locale.ROOT);
        providerEventId = requireText(providerEventId, "provider event id");
        Objects.requireNonNull(eventType, "provider webhook event type is required");
        payloadHash = requireText(payloadHash, "provider webhook payload hash");
        payload = requireText(payload, "provider webhook payload");
        Objects.requireNonNull(receivedAt, "provider webhook received timestamp is required");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
