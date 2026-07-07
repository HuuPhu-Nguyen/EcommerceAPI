package com.phu.ecommerceapi.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void stripeProviderIsRejectedUntilAdapterExists() {
        AppProperties properties = new AppProperties(
                "test",
                "stripe",
                "keycloak",
                new AppProperties.FakeProvider("fake-webhook-secret")
        );

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("supportedPaymentProvider");
    }

    @Test
    void fakeProviderRequiresWebhookSecret() {
        AppProperties properties = new AppProperties(
                "test",
                "fake",
                "keycloak",
                new AppProperties.FakeProvider("")
        );

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fakeProviderWebhookSecretConfiguredWhenRequired");
    }
}
