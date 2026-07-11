package com.phu.ecommerceapi.payment.application;

import java.util.Objects;

public record PaymentRefundProviderResult(
        String providerRefundId,
        PaymentProviderStatus status,
        String failureCode,
        String message
) {

    public PaymentRefundProviderResult {
        if (providerRefundId == null || providerRefundId.isBlank()) {
            throw new IllegalArgumentException("Provider refund id is required");
        }
        Objects.requireNonNull(status, "provider refund status is required");
    }

    public static PaymentRefundProviderResult succeeded(String providerRefundId, String message) {
        return new PaymentRefundProviderResult(providerRefundId, PaymentProviderStatus.SUCCEEDED, null, message);
    }

    public static PaymentRefundProviderResult failed(String providerRefundId, String failureCode, String message) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Provider refund failure code is required");
        }
        return new PaymentRefundProviderResult(providerRefundId, PaymentProviderStatus.FAILED, failureCode, message);
    }

    public static PaymentRefundProviderResult duplicate(String providerRefundId, String message) {
        return new PaymentRefundProviderResult(providerRefundId, PaymentProviderStatus.DUPLICATE, null, message);
    }

    public static PaymentRefundProviderResult pending(String providerRefundId, String message) {
        return new PaymentRefundProviderResult(providerRefundId, PaymentProviderStatus.PENDING, null, message);
    }
}
