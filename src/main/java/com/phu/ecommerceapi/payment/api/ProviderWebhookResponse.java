package com.phu.ecommerceapi.payment.api;

import com.phu.ecommerceapi.payment.application.ProviderWebhookHandlingResponse;

public record ProviderWebhookResponse(
        String providerEventId,
        String status,
        String message
) {

    public static ProviderWebhookResponse from(ProviderWebhookHandlingResponse response) {
        return new ProviderWebhookResponse(
                response.providerEventId(),
                response.status(),
                response.message()
        );
    }
}
