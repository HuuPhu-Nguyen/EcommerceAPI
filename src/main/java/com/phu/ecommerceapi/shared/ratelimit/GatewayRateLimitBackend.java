package com.phu.ecommerceapi.shared.ratelimit;

public final class GatewayRateLimitBackend implements RateLimitBackend {

    @Override
    public String backendName() {
        return "gateway";
    }

    @Override
    public boolean allow(String key, int limit, long windowSeconds) {
        return true;
    }
}
