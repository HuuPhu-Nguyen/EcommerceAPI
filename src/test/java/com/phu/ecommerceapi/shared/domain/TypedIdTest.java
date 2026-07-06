package com.phu.ecommerceapi.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedIdTest {

    @Test
    void positiveLongIdsAreAccepted() {
        assertThat(new CustomerId(1).value()).isEqualTo(1);
        assertThat(new ProductId(2).value()).isEqualTo(2);
        assertThat(new CartId(3).value()).isEqualTo(3);
    }

    @Test
    void positiveLongIdsRejectInvalidValues() {
        assertThatThrownBy(() -> new CustomerId(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Customer id must be positive");
    }

    @Test
    void uuidIdsCanBeGenerated() {
        assertThat(OrderId.newId().value()).isNotNull();
        assertThat(PaymentId.newId().value()).isNotNull();
        assertThat(RefundId.newId().value()).isNotNull();
        assertThat(LedgerTransactionId.newId().value()).isNotNull();
    }
}
