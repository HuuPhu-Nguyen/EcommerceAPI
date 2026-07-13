package com.phu.ecommerceapi.checkout.infrastructure;

import com.phu.ecommerceapi.checkout.application.CheckoutInventoryReservationPort;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import org.springframework.stereotype.Component;

@Component
public class CheckoutInventoryReservationAdapter implements CheckoutInventoryReservationPort {

    private final InventoryReservationService inventoryReservationService;

    public CheckoutInventoryReservationAdapter(InventoryReservationService inventoryReservationService) {
        this.inventoryReservationService = inventoryReservationService;
    }

    @Override
    public void reserve(long productId, int requestedQuantity) {
        inventoryReservationService.reserve(productId, requestedQuantity);
    }
}
