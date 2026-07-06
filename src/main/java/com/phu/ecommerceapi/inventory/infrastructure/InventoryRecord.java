package com.phu.ecommerceapi.inventory.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryRecord {

    @Id
    private Long productId;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private int reservedQuantity;

    protected InventoryRecord() {
    }

    public InventoryRecord(long productId, int availableQuantity, int reservedQuantity) {
        if (availableQuantity < 0 || reservedQuantity < 0) {
            throw new IllegalArgumentException("Inventory quantities cannot be negative");
        }
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }
}
