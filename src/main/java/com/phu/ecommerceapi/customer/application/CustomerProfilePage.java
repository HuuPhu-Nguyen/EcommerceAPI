package com.phu.ecommerceapi.customer.application;

import java.util.List;
import java.util.Objects;

public record CustomerProfilePage(
        List<CustomerProfile> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public CustomerProfilePage {
        items = List.copyOf(Objects.requireNonNull(items, "customer profile page items are required"));
    }
}
