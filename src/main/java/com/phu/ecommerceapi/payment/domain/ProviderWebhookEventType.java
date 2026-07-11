package com.phu.ecommerceapi.payment.domain;

import java.util.Locale;

public enum ProviderWebhookEventType {
    PAYMENT_SUCCEEDED("payment.succeeded"),
    PAYMENT_FAILED("payment.failed"),
    REFUND_SUCCEEDED("refund.succeeded"),
    REFUND_FAILED("refund.failed"),
    UNSUPPORTED("unsupported");

    private final String wireName;

    ProviderWebhookEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ProviderWebhookEventType fromWireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider webhook type is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ProviderWebhookEventType eventType : values()) {
            if (eventType.wireName.equals(normalized)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("unsupported provider webhook type: " + value);
    }
}
