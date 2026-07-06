package com.phu.ecommerceapi.reconciliation.application;

import java.util.Objects;

public record ReconciliationIssue(
        ReconciliationIssueCode code,
        String resourceType,
        String resourceId,
        String message
) {

    public ReconciliationIssue {
        Objects.requireNonNull(code, "reconciliation issue code is required");
        resourceType = requireText(resourceType, "reconciliation issue resource type");
        resourceId = requireText(resourceId, "reconciliation issue resource id");
        message = requireText(message, "reconciliation issue message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
