package com.phu.ecommerceapi.payment.application;

public record ProviderWebhookHandlingResponse(
        String providerEventId,
        String status,
        String message
) {
}
