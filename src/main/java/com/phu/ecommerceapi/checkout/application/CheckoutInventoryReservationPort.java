package com.phu.ecommerceapi.checkout.application;

public interface CheckoutInventoryReservationPort {

    void reserve(long productId, int requestedQuantity);
}
