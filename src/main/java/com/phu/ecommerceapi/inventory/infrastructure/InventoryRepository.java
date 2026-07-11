package com.phu.ecommerceapi.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryRecord, Long> {

    @Query(
            value = """
                    select *
                    from inventory
                    where product_id = :productId
                    for update
                    """,
            nativeQuery = true
    )
    Optional<InventoryRecord> findByProductIdForUpdate(@Param("productId") long productId);

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
