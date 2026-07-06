package com.phu.ecommerceapi.inventory.application;

import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import com.phu.ecommerceapi.shared.domain.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReservationService {

    private final InventoryRepository inventoryRepository;

    public InventoryReservationService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public void initializeInventory(long productId, int availableQuantity) {
        inventoryRepository.save(new InventoryRecord(productId, availableQuantity, 0));
    }

    @Transactional
    public void setAvailableQuantity(long productId, int availableQuantity) {
        int updatedRows = inventoryRepository.updateAvailableQuantity(productId, availableQuantity);
        if (updatedRows == 0) {
            initializeInventory(productId, availableQuantity);
        }
    }

    @Transactional
    public void reserve(long productId, int requestedQuantity) {
        Quantity quantity = Quantity.of(requestedQuantity);
        int updatedRows = inventoryRepository.reserve(productId, quantity.value());
        if (updatedRows == 0) {
            throw new OutOfStockException("Not enough stock is available");
        }
    }

    @Transactional(readOnly = true)
    public InventorySnapshot getInventory(long productId) {
        InventoryRecord inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found"));
        return new InventorySnapshot(
                inventory.getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity()
        );
    }
}
