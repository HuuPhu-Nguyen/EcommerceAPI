package com.phu.ecommerceapi.shared.api;

public record RequestMetadata(
        String requestId,
        String externalCorrelationId,
        String ipAddress,
        String userAgent
) {

    public static RequestMetadata unknown() {
        return new RequestMetadata("unknown", null, "unknown", "unknown");
    }

    public RequestMetadata {
        requestId = normalize(requestId);
        externalCorrelationId = normalizeOptional(externalCorrelationId);
        ipAddress = normalize(ipAddress);
        userAgent = normalize(userAgent);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
