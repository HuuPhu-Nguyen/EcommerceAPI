package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private final AppProperties appProperties;

    public StripeConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        if (!appProperties.isStripeProvider()) {
            return;
        }

        Stripe.apiKey = appProperties.stripe().secretKey();
    }
}
