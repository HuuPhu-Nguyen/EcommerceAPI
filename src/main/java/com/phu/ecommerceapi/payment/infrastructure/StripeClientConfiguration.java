package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.stripe.StripeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class StripeClientConfiguration {

    @Bean
    @Conditional(StripeEnabledCondition.class)
    StripeClient stripeClient(AppProperties appProperties) {
        AppProperties.StripeProviderProperties stripe = appProperties.stripe();
        return StripeClient.builder()
                .setApiKey(stripe.secretKey())
                .setConnectTimeout(stripe.connectTimeoutMs())
                .setReadTimeout(stripe.readTimeoutMs())
                .build();
    }

    private static final class StripeEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String enabledProviders = context.getEnvironment()
                    .getProperty("app.payment-provider.enabled", "");
            return Arrays.stream(enabledProviders.split(","))
                    .map(String::trim)
                    .anyMatch(providerCode -> providerCode.equalsIgnoreCase("stripe"));
        }
    }
}
