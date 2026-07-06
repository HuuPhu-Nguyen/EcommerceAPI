package com.phu.ecommerceapi.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityTest {

    @Test
    void acceptsPositiveQuantity() {
        assertThat(Quantity.of(3).value()).isEqualTo(3);
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        assertThatThrownBy(() -> Quantity.of(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");

        assertThatThrownBy(() -> Quantity.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
    }
}
