package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StripePaymentGatewayConfiguration {

    @Bean
    @ConditionalOnBean(StripeClient.class)
    @ConditionalOnMissingBean(StripePaymentGateway.class)
    StripePaymentGateway stripePaymentGateway(StripeClient stripeClient, AppProperties appProperties) {
        return new StripeClientPaymentGateway(new StripeSdkPaymentIntentClient(stripeClient), appProperties);
    }
}
