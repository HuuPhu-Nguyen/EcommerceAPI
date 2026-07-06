package com.phu.ecommerceapi.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void normalizesEmailAddress() {
        EmailAddress email = EmailAddress.of("  CUSTOMER@Example.COM ");

        assertThat(email.value()).isEqualTo("customer@example.com");
    }

    @Test
    void rejectsInvalidEmailAddress() {
        assertThatThrownBy(() -> EmailAddress.of("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email address is invalid");
    }
}
