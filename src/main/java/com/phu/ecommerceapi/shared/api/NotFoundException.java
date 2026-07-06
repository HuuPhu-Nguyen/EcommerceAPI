package com.phu.ecommerceapi.shared.api;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, message);
    }
}
