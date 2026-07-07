package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;

import java.time.OffsetDateTime;

public record ProviderWebhookRegistrationCommand(
        String providerName,
        String providerEventId,
        ProviderWebhookEventType eventType,
        String payloadHash,
        String payload,
        OffsetDateTime receivedAt
) {
}
