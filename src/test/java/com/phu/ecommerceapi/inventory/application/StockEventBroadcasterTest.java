package com.phu.ecommerceapi.inventory.application;

import com.phu.ecommerceapi.shared.api.RateLimitedException;
import com.phu.ecommerceapi.shared.api.RequestMetadata;
import com.phu.ecommerceapi.shared.api.RequestMetadataHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockEventBroadcasterTest {

    @AfterEach
    void clearRequestMetadata() {
        RequestMetadataHolder.clear();
    }

    @Test
    void publishesStockChangeToSubscribersForSameProduct() {
        StockEventBroadcaster broadcaster = new StockEventBroadcaster(3, 300);
        broadcaster.subscribe(10L, "subject-1");

        int delivered = broadcaster.publish(new StockChangedSseEvent(
                10L,
                4,
                1,
                "RESERVED",
                Instant.parse("2026-07-06T08:00:00Z"),
                true
        ));

        assertThat(delivered).isEqualTo(1);
        assertThat(broadcaster.subscriberCount(10L)).isEqualTo(1);
        assertThat(broadcaster.subscriberCount(10L, StockEventBroadcaster.clientKeyForSubject("subject-1")))
                .isEqualTo(1);
        assertThat(broadcaster.publish(new StockChangedSseEvent(
                20L,
                9,
                0,
                "AVAILABLE_QUANTITY_SET",
                Instant.parse("2026-07-06T08:00:01Z"),
                true
        ))).isZero();
    }

    @Test
    void rejectsSubscriptionsAboveClientLimitForSameProduct() {
        StockEventBroadcaster broadcaster = new StockEventBroadcaster(3, 300);

        broadcaster.subscribe(10L, "subject-1");
        broadcaster.subscribe(10L, "subject-1");
        broadcaster.subscribe(10L, "subject-1");

        assertThatThrownBy(() -> broadcaster.subscribe(10L, "subject-1"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessage("Too many stock stream connections for this client");

        assertThat(broadcaster.subscriberCount(10L, StockEventBroadcaster.clientKeyForSubject("subject-1")))
                .isEqualTo(3);
    }

    @Test
    void limitIsScopedByProductAndClient() {
        StockEventBroadcaster broadcaster = new StockEventBroadcaster(1, 300);

        broadcaster.subscribe(10L, "subject-1");
        broadcaster.subscribe(20L, "subject-1");
        broadcaster.subscribe(10L, "subject-2");

        assertThat(broadcaster.subscriberCount(10L)).isEqualTo(2);
        assertThat(broadcaster.subscriberCount(20L)).isEqualTo(1);
    }

    @Test
    void closingStreamDecrementsSubscriberCount() {
        StockEventBroadcaster broadcaster = new StockEventBroadcaster(3, 300);
        SseEmitter emitter = broadcaster.subscribe(10L, "subject-1");

        assertThat(broadcaster.subscriberCount(10L, StockEventBroadcaster.clientKeyForSubject("subject-1")))
                .isEqualTo(1);

        emitter.complete();

        assertThat(broadcaster.subscriberCount(10L)).isZero();
        assertThat(broadcaster.subscriberCount(10L, StockEventBroadcaster.clientKeyForSubject("subject-1")))
                .isZero();
    }

    @Test
    void unauthenticatedSubscriptionFallsBackToTrustedRequestIp() {
        StockEventBroadcaster broadcaster = new StockEventBroadcaster(3, 300);
        RequestMetadataHolder.set(new RequestMetadata("request-1", null, "203.0.113.10", "JUnit"));

        broadcaster.subscribe(10L);

        assertThat(broadcaster.subscriberCount(10L, StockEventBroadcaster.clientKeyForIp("203.0.113.10")))
                .isEqualTo(1);
    }
}
