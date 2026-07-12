package com.phu.ecommerceapi.reconciliation.application;

import java.util.Locale;

public record ProviderReconciliationReference(
        String providerCode,
        String providerObjectId
) {

    public ProviderReconciliationReference {
        providerCode = normalizeProviderCode(providerCode);
        providerObjectId = requireText(providerObjectId, "provider object id");
    }

    private static String normalizeProviderCode(String value) {
        return requireText(value, "provider code").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
