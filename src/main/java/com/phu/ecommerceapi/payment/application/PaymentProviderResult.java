package com.phu.ecommerceapi.payment.application;

import java.util.Objects;

public record PaymentProviderResult(
        String providerPaymentId,
        PaymentProviderStatus status,
        String providerStatus,
        String failureCode,
        String message
) {

    public PaymentProviderResult {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment id is required");
        }
        Objects.requireNonNull(status, "provider payment status is required");
        if (providerStatus == null || providerStatus.isBlank()) {
            providerStatus = status.name();
        } else {
            providerStatus = providerStatus.trim();
        }
    }

    public static PaymentProviderResult succeeded(String providerPaymentId, String message) {
        return succeeded(providerPaymentId, PaymentProviderStatus.SUCCEEDED.name(), message);
    }

    public static PaymentProviderResult succeeded(String providerPaymentId, String providerStatus, String message) {
        return new PaymentProviderResult(
                providerPaymentId,
                PaymentProviderStatus.SUCCEEDED,
                providerStatus,
                null,
                message
        );
    }

    public static PaymentProviderResult failed(String providerPaymentId, String failureCode, String message) {
        return failed(providerPaymentId, PaymentProviderStatus.FAILED.name(), failureCode, message);
    }

    public static PaymentProviderResult failed(
            String providerPaymentId,
            String providerStatus,
            String failureCode,
            String message
    ) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Provider failure code is required");
        }
        return new PaymentProviderResult(
                providerPaymentId,
                PaymentProviderStatus.FAILED,
                providerStatus,
                failureCode,
                message
        );
    }

    public static PaymentProviderResult duplicate(String providerPaymentId, String message) {
        return new PaymentProviderResult(
                providerPaymentId,
                PaymentProviderStatus.DUPLICATE,
                PaymentProviderStatus.DUPLICATE.name(),
                null,
                message
        );
    }

    public static PaymentProviderResult pending(String providerPaymentId, String providerStatus, String message) {
        return new PaymentProviderResult(
                providerPaymentId,
                PaymentProviderStatus.PENDING,
                providerStatus,
                null,
                message
        );
    }

    public boolean isSuccessful() {
        return status == PaymentProviderStatus.SUCCEEDED;
    }
}
