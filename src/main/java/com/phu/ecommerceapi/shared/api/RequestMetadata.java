package com.phu.ecommerceapi.shared.api;

public record RequestMetadata(
        String requestId,
        String ipAddress,
        String userAgent
) {

    public static RequestMetadata unknown() {
        return new RequestMetadata("unknown", "unknown", "unknown");
    }

    public RequestMetadata {
        requestId = normalize(requestId);
        ipAddress = normalize(ipAddress);
        userAgent = normalize(userAgent);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }
}
