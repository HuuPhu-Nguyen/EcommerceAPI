package com.phu.ecommerceapi.shared.api;

public final class RequestMetadataHolder {

    private static final ThreadLocal<RequestMetadata> CURRENT = new ThreadLocal<>();

    private RequestMetadataHolder() {
    }

    public static void set(RequestMetadata metadata) {
        CURRENT.set(metadata == null ? RequestMetadata.unknown() : metadata);
    }

    public static RequestMetadata current() {
        RequestMetadata metadata = CURRENT.get();
        return metadata == null ? RequestMetadata.unknown() : metadata;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
