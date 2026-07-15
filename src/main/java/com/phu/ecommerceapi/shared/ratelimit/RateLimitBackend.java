package com.phu.ecommerceapi.shared.ratelimit;

public interface RateLimitBackend {
    String backendName();

    boolean allow(String key, int limit, long windowSeconds);
}
