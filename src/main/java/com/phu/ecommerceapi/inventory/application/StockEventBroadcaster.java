package com.phu.ecommerceapi.inventory.application;

import com.phu.ecommerceapi.shared.api.RateLimitedException;
import com.phu.ecommerceapi.shared.api.RequestMetadataHolder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class StockEventBroadcaster implements StockEventPublisher {

    private static final int DEFAULT_MAX_CONNECTIONS_PER_CLIENT = 3;
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    private final int maxConnectionsPerClient;
    private final long streamTimeoutMillis;
    private final Map<Long, CopyOnWriteArrayList<Subscriber>> subscribersByProduct = new ConcurrentHashMap<>();
    private final Map<SubscriberKey, Integer> subscriberCountsByClient = new ConcurrentHashMap<>();
    private final Object subscriptionMonitor = new Object();

    @Autowired
    public StockEventBroadcaster(
            @Value("${app.stock-stream.max-connections-per-client:3}") int maxConnectionsPerClient,
            @Value("${app.stock-stream.timeout-seconds:300}") long timeoutSeconds
    ) {
        this.maxConnectionsPerClient = Math.max(1, maxConnectionsPerClient);
        this.streamTimeoutMillis = Duration.ofSeconds(Math.max(1, timeoutSeconds)).toMillis();
    }

    StockEventBroadcaster() {
        this(DEFAULT_MAX_CONNECTIONS_PER_CLIENT, DEFAULT_TIMEOUT_SECONDS);
    }

    public SseEmitter subscribe(long productId) {
        return subscribe(productId, null);
    }

    public SseEmitter subscribe(long productId, String authenticatedSubject) {
        String clientKey = clientKey(authenticatedSubject);
        ManagedSseEmitter emitter = new ManagedSseEmitter(streamTimeoutMillis);
        Subscriber subscriber = reserve(productId, clientKey, emitter);
        emitter.onClose(() -> remove(subscriber));
        emitter.onCompletion(() -> remove(subscriber));
        emitter.onTimeout(() -> remove(subscriber));
        emitter.onError(error -> remove(subscriber));
        sendConnectedEvent(productId, emitter);
        return emitter;
    }

    @Override
    public int publish(StockChangedSseEvent event) {
        List<Subscriber> subscribers = subscribersByProduct.getOrDefault(event.productId(), new CopyOnWriteArrayList<>());
        int delivered = 0;
        for (Subscriber subscriber : subscribers) {
            if (sendStockChangedEvent(event, subscriber.emitter())) {
                delivered++;
            } else {
                remove(subscriber);
            }
        }
        return delivered;
    }

    public int subscriberCount(long productId) {
        return subscribersByProduct.getOrDefault(productId, new CopyOnWriteArrayList<>()).size();
    }

    public int subscriberCount(long productId, String clientKey) {
        return subscriberCountsByClient.getOrDefault(new SubscriberKey(productId, clientKey), 0);
    }

    public static String clientKeyForSubject(String subject) {
        String normalizedSubject = normalizeRequired(subject, "subject");
        return "subject:" + normalizedSubject;
    }

    public static String clientKeyForIp(String ipAddress) {
        String normalizedIpAddress = normalizeRequired(ipAddress, "ip address");
        return "ip:" + normalizedIpAddress;
    }

    @PreDestroy
    public void completeAll() {
        subscribersByProduct.values()
                .stream()
                .flatMap(List::stream)
                .map(Subscriber::emitter)
                .forEach(SseEmitter::complete);
        subscribersByProduct.clear();
        subscriberCountsByClient.clear();
    }

    private Subscriber reserve(long productId, String clientKey, SseEmitter emitter) {
        SubscriberKey subscriberKey = new SubscriberKey(productId, clientKey);
        synchronized (subscriptionMonitor) {
            int currentCount = subscriberCountsByClient.getOrDefault(subscriberKey, 0);
            if (currentCount >= maxConnectionsPerClient) {
                throw new RateLimitedException("Too many stock stream connections for this client");
            }

            Subscriber subscriber = new Subscriber(productId, clientKey, emitter);
            subscribersByProduct.computeIfAbsent(productId, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
            subscriberCountsByClient.put(subscriberKey, currentCount + 1);
            return subscriber;
        }
    }

    private String clientKey(String authenticatedSubject) {
        String subject = normalizeOptional(authenticatedSubject);
        if (subject != null) {
            return clientKeyForSubject(subject);
        }
        return clientKeyForIp(RequestMetadataHolder.current().ipAddress());
    }

    private void sendConnectedEvent(long productId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(new StockStreamConnectedEvent(productId, Instant.now(), true), MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private boolean sendStockChangedEvent(StockChangedSseEvent event, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("stock-changed")
                    .id(event.productId() + "-" + event.occurredAt().toEpochMilli())
                    .data(event, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return false;
        }
    }

    private void remove(Subscriber subscriber) {
        if (!subscriber.removed().compareAndSet(false, true)) {
            return;
        }
        synchronized (subscriptionMonitor) {
            List<Subscriber> subscribers = subscribersByProduct.get(subscriber.productId());
            if (subscribers != null) {
                subscribers.remove(subscriber);
                if (subscribers.isEmpty()) {
                    subscribersByProduct.remove(subscriber.productId());
                }
            }

            SubscriberKey subscriberKey = new SubscriberKey(subscriber.productId(), subscriber.clientKey());
            Integer count = subscriberCountsByClient.get(subscriberKey);
            if (count == null || count <= 1) {
                subscriberCountsByClient.remove(subscriberKey);
            } else {
                subscriberCountsByClient.put(subscriberKey, count - 1);
            }
        }
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record StockStreamConnectedEvent(
            long productId,
            Instant connectedAt,
            boolean advisory
    ) {
    }

    private record Subscriber(
            long productId,
            String clientKey,
            SseEmitter emitter,
            AtomicBoolean removed
    ) {

        private Subscriber(long productId, String clientKey, SseEmitter emitter) {
            this(productId, clientKey, emitter, new AtomicBoolean(false));
        }
    }

    private record SubscriberKey(long productId, String clientKey) {
    }

    private static final class ManagedSseEmitter extends SseEmitter {

        private final AtomicBoolean closed = new AtomicBoolean(false);
        private Runnable closeHandler = () -> {
        };

        private ManagedSseEmitter(long timeoutMillis) {
            super(timeoutMillis);
        }

        private void onClose(Runnable closeHandler) {
            this.closeHandler = closeHandler;
        }

        @Override
        public void complete() {
            closeOnce();
            super.complete();
        }

        @Override
        public void completeWithError(Throwable ex) {
            closeOnce();
            super.completeWithError(ex);
        }

        private void closeOnce() {
            if (closed.compareAndSet(false, true)) {
                closeHandler.run();
            }
        }
    }
}
