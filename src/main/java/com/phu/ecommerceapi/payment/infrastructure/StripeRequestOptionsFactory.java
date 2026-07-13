package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.stripe.net.RequestOptions;

final class StripeRequestOptionsFactory {

    private final String apiVersion;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    StripeRequestOptionsFactory(AppProperties appProperties) {
        AppProperties.StripeProviderProperties stripe = appProperties.stripe();
        this.apiVersion = stripe.apiVersion();
        this.connectTimeoutMs = stripe.connectTimeoutMs();
        this.readTimeoutMs = stripe.readTimeoutMs();
    }

    RequestOptions requestOptions() {
        return requestOptions(null);
    }

    RequestOptions requestOptions(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder()
                .setConnectTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        if (!apiVersion.isBlank()) {
            RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(builder, apiVersion);
        }
        return builder.build();
    }
}
