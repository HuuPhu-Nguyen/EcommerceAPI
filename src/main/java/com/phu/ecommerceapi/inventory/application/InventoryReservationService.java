package com.phu.ecommerceapi.inventory.application;

import com.phu.ecommerceapi.outbox.application.OutboxEventRecorder;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import com.phu.ecommerceapi.shared.domain.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReservationService {

    private static final String STOCK_CHANGED_EVENT_TYPE = "StockChanged";
    private static final String PRODUCT_AGGREGATE_TYPE = "PRODUCT";

    private final InventoryPersistencePort inventoryPersistencePort;
    private final OutboxEventRecorder outboxEventRecorder;

    public InventoryReservationService(
            InventoryPersistencePort inventoryPersistencePort,
            OutboxEventRecorder outboxEventRecorder
    ) {
        this.inventoryPersistencePort = inventoryPersistencePort;
        this.outboxEventRecorder = outboxEventRecorder;
    }

    @Transactional
    public void initializeInventory(long productId, int availableQuantity) {
        InventoryState inventory = inventoryPersistencePort.initialize(productId, availableQuantity);
        recordStockChanged(inventory, "INITIALIZED");
    }

    @Transactional
    public void setAvailableQuantity(long productId, int availableQuantity) {
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative");
        }
        boolean updated = inventoryPersistencePort.setAvailableQuantity(productId, availableQuantity);
        if (!updated) {
            initializeInventory(productId, availableQuantity);
            return;
        }
        recordStockChanged(inventory(productId), "AVAILABLE_QUANTITY_SET");
    }

    @Transactional
    public void reserve(long productId, int requestedQuantity) {
        Quantity quantity = Quantity.of(requestedQuantity);
        boolean reserved = inventoryPersistencePort.reserve(productId, quantity.value());
        if (!reserved) {
            throw new OutOfStockException("Not enough stock is available");
        }
        recordStockChanged(inventory(productId), "RESERVED");
    }

    @Transactional(readOnly = true)
    public InventorySnapshot getInventory(long productId) {
        InventoryState inventory = inventory(productId);
        return new InventorySnapshot(
                inventory.productId(),
                inventory.availableQuantity(),
                inventory.reservedQuantity()
        );
    }

    private InventoryState inventory(long productId) {
        return inventoryPersistencePort.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found"));
    }

    private void recordStockChanged(InventoryState inventory, String reason) {
        outboxEventRecorder.record(
                PRODUCT_AGGREGATE_TYPE,
                Long.toString(inventory.productId()),
                STOCK_CHANGED_EVENT_TYPE,
                new StockChangedEventPayload(
                        inventory.productId(),
                        inventory.availableQuantity(),
                        inventory.reservedQuantity(),
                        reason
                )
        );
    }
}
