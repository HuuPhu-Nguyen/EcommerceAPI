package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record StripeWebhookEvent(
        String eventId,
        ProviderWebhookEventType eventType,
        OffsetDateTime createdAt,
        String providerEventType,
        String providerObjectId,
        String providerObjectType,
        UUID paymentId,
        UUID refundId,
        String providerPaymentId,
        String providerRefundId,
        String objectStatus,
        String failureCode,
        String message
) {

    public StripeWebhookEvent {
        eventId = requireText(eventId, "Stripe event id");
        Objects.requireNonNull(eventType, "Stripe webhook event type is required");
        providerEventType = requireText(providerEventType, "Stripe event type");
        providerObjectId = trimToNull(providerObjectId);
        providerObjectType = trimToNull(providerObjectType);
        providerPaymentId = trimToNull(providerPaymentId);
        providerRefundId = trimToNull(providerRefundId);
        objectStatus = trimToNull(objectStatus);
        failureCode = trimToNull(failureCode);
        message = trimToNull(message);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
