package com.phu.ecommerceapi.inventory.application;

import java.util.Optional;

public interface InventoryPersistencePort {

    InventoryState initialize(long productId, int availableQuantity);

    InventoryState setAvailableQuantity(long productId, int availableQuantity);

    boolean reserve(long productId, int requestedQuantity);

    Optional<InventoryState> findByProductId(long productId);
}
