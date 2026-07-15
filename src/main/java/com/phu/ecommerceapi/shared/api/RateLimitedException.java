package com.phu.ecommerceapi.shared.api;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends ApiException {

    public RateLimitedException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMITED, message);
    }
}
