package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;

import java.util.UUID;

public record ProviderWebhookEventView(
        UUID eventId,
        String providerEventId,
        ProviderWebhookProcessingStatus processingStatus,
        String processingMessage
) {
}
