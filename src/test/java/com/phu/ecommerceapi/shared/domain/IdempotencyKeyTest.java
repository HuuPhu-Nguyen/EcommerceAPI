package com.phu.ecommerceapi.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void trimsValidIdempotencyKey() {
        IdempotencyKey key = IdempotencyKey.of("  payment-123  ");

        assertThat(key.value()).isEqualTo("payment-123");
    }

    @Test
    void rejectsShortIdempotencyKey() {
        assertThatThrownBy(() -> IdempotencyKey.of("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key length must be between 8 and 128 characters");
    }
}
