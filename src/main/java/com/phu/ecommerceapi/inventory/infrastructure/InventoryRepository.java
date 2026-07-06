package com.phu.ecommerceapi.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryRecord, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryRecord inventory
               set inventory.availableQuantity = :availableQuantity
             where inventory.productId = :productId
            """)
    int updateAvailableQuantity(
            @Param("productId") long productId,
            @Param("availableQuantity") int availableQuantity
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryRecord inventory
               set inventory.availableQuantity = inventory.availableQuantity - :quantity,
                   inventory.reservedQuantity = inventory.reservedQuantity + :quantity
             where inventory.productId = :productId
               and inventory.availableQuantity >= :quantity
            """)
    int reserve(
            @Param("productId") long productId,
            @Param("quantity") int quantity
    );
}
