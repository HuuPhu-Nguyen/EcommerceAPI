package com.phu.ecommerceapi.payment.api;

public record ProviderWebhookResponse(
        String providerEventId,
        String status,
        String message
) {
}
