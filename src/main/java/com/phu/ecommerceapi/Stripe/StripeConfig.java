package com.phu.ecommerceapi.Stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {
    @PostConstruct
    public void init() {
        String stripeApiKey= System.getenv("stripe_secret_key");
        if (stripeApiKey == null || stripeApiKey.isEmpty()) {
            throw new IllegalStateException("Environment variable stripe_secret_key is not set!");
        }
        Stripe.apiKey = stripeApiKey;
        System.out.println("Stripe initialized successfully");
    }
}
