package com.phu.ecommerceapi.payment.application;

import java.util.Objects;

public record PaymentProviderResult(
        String providerPaymentId,
        PaymentProviderStatus status,
        String failureCode,
        String message
) {

    public PaymentProviderResult {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment id is required");
        }
        Objects.requireNonNull(status, "provider payment status is required");
    }

    public static PaymentProviderResult succeeded(String providerPaymentId, String message) {
        return new PaymentProviderResult(providerPaymentId, PaymentProviderStatus.SUCCEEDED, null, message);
    }

    public static PaymentProviderResult failed(String providerPaymentId, String failureCode, String message) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Provider failure code is required");
        }
        return new PaymentProviderResult(providerPaymentId, PaymentProviderStatus.FAILED, failureCode, message);
    }

    public static PaymentProviderResult duplicate(String providerPaymentId, String message) {
        return new PaymentProviderResult(providerPaymentId, PaymentProviderStatus.DUPLICATE, null, message);
    }

    public boolean isSuccessful() {
        return status == PaymentProviderStatus.SUCCEEDED;
    }
}
