package com.phu.ecommerceapi.inventory.infrastructure;

import com.phu.ecommerceapi.inventory.application.InventoryPersistencePort;
import com.phu.ecommerceapi.inventory.application.InventoryState;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaInventoryPersistenceAdapter implements InventoryPersistencePort {

    private final InventoryRepository inventoryRepository;

    public JpaInventoryPersistenceAdapter(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryState initialize(long productId, int availableQuantity) {
        InventoryRecord inventory = inventoryRepository.save(new InventoryRecord(productId, availableQuantity, 0));
        return toState(inventory);
    }

    @Override
    public InventoryState setAvailableQuantity(long productId, int availableQuantity) {
        InventoryRecord inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory row missing for product " + productId
                ));
        inventory.setAvailableQuantity(availableQuantity);
        return toState(inventory);
    }

    @Override
    public boolean reserve(long productId, int requestedQuantity) {
        return inventoryRepository.reserve(productId, requestedQuantity) == 1;
    }

    @Override
    public Optional<InventoryState> findByProductId(long productId) {
        return inventoryRepository.findById(productId)
                .map(this::toState);
    }

    private InventoryState toState(InventoryRecord inventory) {
        return new InventoryState(
                inventory.getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity()
        );
    }
}
