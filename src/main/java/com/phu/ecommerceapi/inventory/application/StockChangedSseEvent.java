package com.phu.ecommerceapi.inventory.application;

import java.time.Instant;

public record StockChangedSseEvent(
        long productId,
        int availableQuantity,
        int reservedQuantity,
        String reason,
        Instant occurredAt,
        boolean advisory
) {
}
