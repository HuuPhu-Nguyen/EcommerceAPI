package com.phu.ecommerceapi.shared.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRateLimitBackend implements RateLimitBackend {

    private final Clock clock;
    private final int maxCounterKeys;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Object counterMaintenanceMonitor = new Object();

    public InMemoryRateLimitBackend(Clock clock, int maxCounterKeys) {
        this.clock = clock;
        this.maxCounterKeys = Math.max(1, maxCounterKeys);
    }

    @Override
    public String backendName() {
        return "in-memory";
    }

    @Override
    public boolean allow(String key, int limit, long windowSeconds) {
        long normalizedWindowSeconds = Math.max(1, windowSeconds);
        int normalizedLimit = Math.max(1, limit);
        long now = clock.instant().getEpochSecond();
        WindowCounter counter = counterFor(key, now, normalizedWindowSeconds);
        if (counter == null) {
            return false;
        }
        synchronized (counter) {
            if (now - counter.windowStartedAt >= normalizedWindowSeconds) {
                counter.windowStartedAt = now;
                counter.count = 0;
            }
            counter.lastSeenAt = now;
            if (counter.count >= normalizedLimit) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    private WindowCounter counterFor(String key, long now, long windowSeconds) {
        WindowCounter existingCounter = counters.get(key);
        if (existingCounter != null) {
            return existingCounter;
        }

        synchronized (counterMaintenanceMonitor) {
            existingCounter = counters.get(key);
            if (existingCounter != null) {
                return existingCounter;
            }

            evictExpiredCounters(now, windowSeconds);
            if (counters.size() >= maxCounterKeys) {
                return null;
            }

            WindowCounter newCounter = new WindowCounter(now);
            counters.put(key, newCounter);
            return newCounter;
        }
    }

    private void evictExpiredCounters(long now, long windowSeconds) {
        counters.entrySet().removeIf(entry -> now - entry.getValue().lastSeenAt >= windowSeconds);
    }

    private static final class WindowCounter {
        private long windowStartedAt;
        private long lastSeenAt;
        private int count;

        private WindowCounter(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
            this.lastSeenAt = windowStartedAt;
        }
    }
}
