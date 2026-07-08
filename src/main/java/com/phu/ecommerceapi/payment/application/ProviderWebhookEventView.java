package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;

import java.util.UUID;

public record ProviderWebhookEventView(
        UUID eventId,
        String providerCode,
        String providerEventId,
        ProviderWebhookEventType eventType,
        ProviderWebhookProcessingStatus processingStatus,
        String processingMessage
) {
}
