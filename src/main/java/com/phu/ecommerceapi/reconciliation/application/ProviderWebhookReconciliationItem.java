package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;

import java.util.UUID;

public record ProviderWebhookReconciliationItem(
        UUID id,
        String providerCode,
        ProviderWebhookEventType eventType,
        ProviderWebhookProcessingStatus processingStatus,
        String providerObjectId,
        String providerObjectType
) {
}
