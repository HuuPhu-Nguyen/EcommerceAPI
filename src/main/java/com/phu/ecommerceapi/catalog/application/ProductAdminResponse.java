package com.phu.ecommerceapi.catalog.application;

public record ProductAdminResponse(
        long id,
        String name,
        double price,
        double stock,
        boolean active
) {
}
