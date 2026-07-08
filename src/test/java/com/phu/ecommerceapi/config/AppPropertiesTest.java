package com.phu.ecommerceapi.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingProdPaymentProviderActiveFailsValidation() {
        AppProperties properties = properties(
                "prod",
                "",
                List.of("fake"),
                "fake-webhook-secret"
        );

        assertThat(paths(properties)).contains("paymentProvider.active");
    }

    @Test
    void missingProdPaymentProviderEnabledFailsValidation() {
        AppProperties properties = properties(
                "prod",
                "fake",
                List.of(),
                "fake-webhook-secret"
        );

        assertThat(paths(properties)).contains("paymentProvider.enabled");
    }

    @Test
    void activeProviderMustBeEnabled() {
        AppProperties properties = properties(
                "prod",
                "stripe",
                List.of("fake"),
                "fake-webhook-secret"
        );

        assertThat(messages(properties))
                .contains("app.payment-provider.active must be included in app.payment-provider.enabled");
    }

    @Test
    void enabledProvidersAreNormalizedAndDeduplicated() {
        AppProperties.PaymentProviderProperties properties = new AppProperties.PaymentProviderProperties(
                " FAKE ",
                List.of("fake,stripe,fake", "STRIPE")
        );

        assertThat(properties.active()).isEqualTo("fake");
        assertThat(properties.enabled()).containsExactly("fake", "stripe");
    }

    @Test
    void unknownEnabledProviderFailsValidation() {
        AppProperties properties = properties(
                "prod",
                "fake",
                List.of("fake", "unknown"),
                "fake-webhook-secret"
        );

        assertThat(messages(properties))
                .contains("app.payment-provider.enabled must contain only fake,stripe and no blank provider codes");
    }

    @Test
    void fakeProviderRequiresWebhookSecretWhenEnabled() {
        AppProperties properties = properties(
                "prod",
                "fake",
                List.of("fake"),
                ""
        );

        assertThat(messages(properties))
                .contains(
                        "app.fake-provider.webhook-secret is required when "
                                + "app.payment-provider.enabled contains fake"
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void profileDefaultConfigStartsWithFakeProviderSettings(String profile) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AppPropertiesConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties properties = context.getBean(AppProperties.class);

                    assertThat(properties.paymentProvider().active()).isEqualTo("fake");
                    assertThat(properties.paymentProvider().enabled()).containsExactly("fake");
                    assertThat(properties.fakeProvider().webhookSecret()).isNotBlank();
                });
    }

    private AppProperties properties(String environment, String active, List<String> enabled, String webhookSecret) {
        return new AppProperties(
                environment,
                "keycloak",
                new AppProperties.PaymentProviderProperties(active, enabled),
                new AppProperties.FakeProvider(webhookSecret)
        );
    }

    private Set<String> paths(AppProperties properties) {
        return validator.validate(properties)
                .stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private Set<String> messages(AppProperties properties) {
        return validator.validate(properties)
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class AppPropertiesConfiguration {
    }
}
