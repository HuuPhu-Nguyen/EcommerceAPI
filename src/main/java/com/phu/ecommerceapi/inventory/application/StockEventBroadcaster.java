package com.phu.ecommerceapi.inventory.application;

import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class StockEventBroadcaster implements StockEventPublisher {

    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByProduct = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long productId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        emittersByProduct.computeIfAbsent(productId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(productId, emitter));
        emitter.onTimeout(() -> remove(productId, emitter));
        emitter.onError(error -> remove(productId, emitter));
        sendConnectedEvent(productId, emitter);
        return emitter;
    }

    @Override
    public int publish(StockChangedSseEvent event) {
        List<SseEmitter> emitters = emittersByProduct.getOrDefault(event.productId(), new CopyOnWriteArrayList<>());
        int delivered = 0;
        for (SseEmitter emitter : emitters) {
            if (sendStockChangedEvent(event, emitter)) {
                delivered++;
            } else {
                remove(event.productId(), emitter);
            }
        }
        return delivered;
    }

    public int subscriberCount(long productId) {
        return emittersByProduct.getOrDefault(productId, new CopyOnWriteArrayList<>()).size();
    }

    @PreDestroy
    public void completeAll() {
        emittersByProduct.values()
                .stream()
                .flatMap(List::stream)
                .forEach(SseEmitter::complete);
        emittersByProduct.clear();
    }

    private void sendConnectedEvent(long productId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(new StockStreamConnectedEvent(productId, Instant.now(), true), MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            remove(productId, emitter);
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

    private void remove(long productId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByProduct.get(productId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByProduct.remove(productId);
        }
    }

    private record StockStreamConnectedEvent(
            long productId,
            Instant connectedAt,
            boolean advisory
    ) {
    }
}
