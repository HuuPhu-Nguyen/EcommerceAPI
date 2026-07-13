package com.phu.ecommerceapi.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;

public class FakeProviderEnabledCondition implements Condition {

    private static final String FAKE_PROVIDER_CODE = "fake";
    private static final String ENABLED_PROVIDERS_PROPERTY = "app.payment-provider.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isFakeProviderEnabled(context.getEnvironment());
    }

    public static boolean isFakeProviderEnabled(Environment environment) {
        return isProviderEnabled(environment, FAKE_PROVIDER_CODE);
    }

    private static boolean isProviderEnabled(Environment environment, String providerCode) {
        String enabledProviders = environment.getProperty(ENABLED_PROVIDERS_PROPERTY, "");
        return Arrays.stream(enabledProviders.split(","))
                .map(String::trim)
                .anyMatch(enabledProvider -> enabledProvider.equalsIgnoreCase(providerCode));
    }
}
