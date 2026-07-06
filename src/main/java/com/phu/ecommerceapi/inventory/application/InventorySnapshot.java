package com.phu.ecommerceapi.inventory.application;

public record InventorySnapshot(
        long productId,
        int availableQuantity,
        int reservedQuantity
) {
}
