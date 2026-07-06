package com.phu.ecommerceapi.catalog.application;

public record ProductCatalogItem(
        long id,
        String name,
        double price,
        double stock
) {
}
