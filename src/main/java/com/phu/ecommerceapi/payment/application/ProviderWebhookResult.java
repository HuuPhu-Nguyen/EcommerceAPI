package com.phu.ecommerceapi.payment.application;

import java.util.Objects;

public record ProviderWebhookResult(
        int httpStatus,
        ProviderWebhookHandlingResponse response
) {

    public ProviderWebhookResult {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("provider webhook HTTP status is invalid");
        }
        Objects.requireNonNull(response, "provider webhook response is required");
    }
}
