package com.phu.ecommerceapi.shared.api;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
