package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.api.ProviderWebhookResponse;

public record FakeProviderWebhookResult(
        int httpStatus,
        ProviderWebhookResponse response
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
