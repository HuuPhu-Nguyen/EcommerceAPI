package com.phu.ecommerceapi.shared.domain;

public record IdempotencyKey(String value) {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        value = value.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Idempotency key length must be between 8 and 128 characters");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }
}
