package com.phu.ecommerceapi.shared.api;

import org.springframework.http.HttpStatus;

public class OutOfStockException extends ApiException {

    public OutOfStockException(String message) {
        super(HttpStatus.CONFLICT, ApiErrorCode.OUT_OF_STOCK, message);
    }
}
