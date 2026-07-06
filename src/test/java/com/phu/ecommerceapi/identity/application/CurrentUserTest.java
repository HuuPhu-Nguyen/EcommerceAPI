package com.phu.ecommerceapi.identity.application;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @Test
    void normalizesRolesAndScopesForCaseInsensitiveChecks() {
        CurrentUser currentUser = new CurrentUser(
                " subject-1 ",
                " customer@example.com ",
                " customer@example.com ",
                Set.of(" CUSTOMER ", "Admin"),
                Set.of(" catalog:read ", "CHECKOUT:WRITE")
        );

        assertThat(currentUser.subject()).isEqualTo("subject-1");
        assertThat(currentUser.username()).isEqualTo("customer@example.com");
        assertThat(currentUser.email()).isEqualTo("customer@example.com");
        assertThat(currentUser.hasRole("customer")).isTrue();
        assertThat(currentUser.hasRole("ADMIN")).isTrue();
        assertThat(currentUser.hasScope("catalog:read")).isTrue();
        assertThat(currentUser.hasScope("checkout:write")).isTrue();
    }

    @Test
    void requiresSubject() {
        assertThatThrownBy(() -> new CurrentUser(" ", null, null, Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subject is required");
    }
}
