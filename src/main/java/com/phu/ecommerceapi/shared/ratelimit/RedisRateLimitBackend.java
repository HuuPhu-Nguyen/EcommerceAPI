package com.phu.ecommerceapi.shared.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public final class RedisRateLimitBackend implements RateLimitBackend {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitBackend(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String backendName() {
        return "redis";
    }

    @Override
    public boolean allow(String key, int limit, long windowSeconds) {
        int normalizedLimit = Math.max(1, limit);
        long normalizedWindowSeconds = Math.max(1, windowSeconds);
        Long count = redisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(redisKey(key)),
                Long.toString(normalizedWindowSeconds)
        );
        return count != null && count <= normalizedLimit;
    }

    private String redisKey(String key) {
        return "ecommerce-api:rate-limit:" + key;
    }
}
