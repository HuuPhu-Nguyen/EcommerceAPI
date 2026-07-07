package com.phu.ecommerceapi.inventory.application;

public record InventoryState(
        long productId,
        int availableQuantity,
        int reservedQuantity
) {
}
