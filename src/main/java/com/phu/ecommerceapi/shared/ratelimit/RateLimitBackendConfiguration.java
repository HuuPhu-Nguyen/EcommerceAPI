package com.phu.ecommerceapi.shared.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
public class RateLimitBackendConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "app.security.rate-limit.backend",
            havingValue = "in-memory",
            matchIfMissing = true
    )
    RateLimitBackend inMemoryRateLimitBackend(
            @Value("${app.security.rate-limit.max-keys:10000}") int maxCounterKeys
    ) {
        return new InMemoryRateLimitBackend(Clock.systemUTC(), maxCounterKeys);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "gateway")
    RateLimitBackend gatewayRateLimitBackend() {
        return new GatewayRateLimitBackend();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
    RateLimitBackend redisRateLimitBackend(StringRedisTemplate redisTemplate) {
        return new RedisRateLimitBackend(redisTemplate);
    }
}
