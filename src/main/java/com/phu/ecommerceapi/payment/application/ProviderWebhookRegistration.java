package com.phu.ecommerceapi.payment.application;

public record ProviderWebhookRegistration(
        ProviderWebhookEventView event,
        boolean inserted,
        boolean payloadMatched
) {
}
