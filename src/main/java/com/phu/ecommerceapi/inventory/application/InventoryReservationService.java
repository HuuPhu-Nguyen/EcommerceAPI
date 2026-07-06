package com.phu.ecommerceapi.inventory.application;

import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
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

    private final InventoryRepository inventoryRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    public InventoryReservationService(
            InventoryRepository inventoryRepository,
            OutboxEventRecorder outboxEventRecorder
    ) {
        this.inventoryRepository = inventoryRepository;
        this.outboxEventRecorder = outboxEventRecorder;
    }

    @Transactional
    public void initializeInventory(long productId, int availableQuantity) {
        InventoryRecord inventory = inventoryRepository.save(new InventoryRecord(productId, availableQuantity, 0));
        recordStockChanged(inventory, "INITIALIZED");
    }

    @Transactional
    public void setAvailableQuantity(long productId, int availableQuantity) {
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative");
        }
        int updatedRows = inventoryRepository.updateAvailableQuantity(productId, availableQuantity);
        if (updatedRows == 0) {
            initializeInventory(productId, availableQuantity);
            return;
        }
        recordStockChanged(inventory(productId), "AVAILABLE_QUANTITY_SET");
    }

    @Transactional
    public void reserve(long productId, int requestedQuantity) {
        Quantity quantity = Quantity.of(requestedQuantity);
        int updatedRows = inventoryRepository.reserve(productId, quantity.value());
        if (updatedRows == 0) {
            throw new OutOfStockException("Not enough stock is available");
        }
        recordStockChanged(inventory(productId), "RESERVED");
    }

    @Transactional(readOnly = true)
    public InventorySnapshot getInventory(long productId) {
        InventoryRecord inventory = inventory(productId);
        return new InventorySnapshot(
                inventory.getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity()
        );
    }

    private InventoryRecord inventory(long productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found"));
    }

    private void recordStockChanged(InventoryRecord inventory, String reason) {
        outboxEventRecorder.record(
                PRODUCT_AGGREGATE_TYPE,
                Long.toString(inventory.getProductId()),
                STOCK_CHANGED_EVENT_TYPE,
                new StockChangedEventPayload(
                        inventory.getProductId(),
                        inventory.getAvailableQuantity(),
                        inventory.getReservedQuantity(),
                        reason
                )
        );
    }
}
