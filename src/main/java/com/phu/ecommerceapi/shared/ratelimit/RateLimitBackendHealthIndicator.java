package com.phu.ecommerceapi.shared.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RateLimitBackendHealthIndicator implements HealthIndicator {

    private final RateLimitBackend rateLimitBackend;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public RateLimitBackendHealthIndicator(
            RateLimitBackend rateLimitBackend,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        this.rateLimitBackend = rateLimitBackend;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public Health health() {
        if (!(rateLimitBackend instanceof RedisRateLimitBackend)) {
            return Health.up()
                    .withDetail("backend", rateLimitBackend.backendName())
                    .build();
        }

        try {
            return redisHealth();
        } catch (RuntimeException exception) {
            return Health.down(exception)
                    .withDetail("backend", rateLimitBackend.backendName())
                    .build();
        }
    }

    private Health redisHealth() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getObject();
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return Health.down()
                    .withDetail("backend", rateLimitBackend.backendName())
                    .withDetail("error", "Redis connection factory is not configured")
                    .build();
        }

        RedisConnection connection = connectionFactory.getConnection();
        try {
            String pong = connection.ping();
            return Health.up()
                    .withDetail("backend", rateLimitBackend.backendName())
                    .withDetail("ping", pong)
                    .build();
        } finally {
            connection.close();
        }
    }
}
