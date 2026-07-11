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
        OffsetDateTime receivedAt,
        OffsetDateTime providerEventCreatedAt,
        String providerEventType,
        String providerObjectId,
        String providerObjectType
) {

    public ProviderWebhookRegistrationCommand(
            String providerCode,
            String providerEventId,
            ProviderWebhookEventType eventType,
            String payloadHash,
            String payload,
            OffsetDateTime receivedAt
    ) {
        this(providerCode, providerEventId, eventType, payloadHash, payload, receivedAt, null, null, null, null);
    }

    public ProviderWebhookRegistrationCommand {
        providerCode = requireText(providerCode, "provider code").toLowerCase(Locale.ROOT);
        providerEventId = requireText(providerEventId, "provider event id");
        Objects.requireNonNull(eventType, "provider webhook event type is required");
        payloadHash = requireText(payloadHash, "provider webhook payload hash");
        payload = requireText(payload, "provider webhook payload");
        Objects.requireNonNull(receivedAt, "provider webhook received timestamp is required");
        providerEventType = trimToNull(providerEventType);
        providerObjectId = trimToNull(providerObjectId);
        providerObjectType = trimToNull(providerObjectType);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
