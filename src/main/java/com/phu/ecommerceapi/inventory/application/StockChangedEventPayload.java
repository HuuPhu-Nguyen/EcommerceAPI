package com.phu.ecommerceapi.inventory.application;

public record StockChangedEventPayload(
        long productId,
        int availableQuantity,
        int reservedQuantity,
        String reason
) {
}
