package com.phu.ecommerceapi.inventory.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockEventBroadcasterTest {

    @Test
    void publishesStockChangeToSubscribersForSameProduct() {
        StockEventBroadcaster broadcaster = new StockEventBroadcaster();
        broadcaster.subscribe(10L);

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
        assertThat(broadcaster.publish(new StockChangedSseEvent(
                20L,
                9,
                0,
                "AVAILABLE_QUANTITY_SET",
                Instant.parse("2026-07-06T08:00:01Z"),
                true
        ))).isZero();
    }
}
