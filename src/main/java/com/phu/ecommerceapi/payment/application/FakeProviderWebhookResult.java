package com.phu.ecommerceapi.payment.application;

public record FakeProviderWebhookResult(
        int httpStatus,
        ProviderWebhookHandlingResponse response
) {

    public FakeProviderWebhookResult {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status must be valid");
        }
        if (response == null) {
            throw new IllegalArgumentException("provider webhook response is required");
        }
    }
}
