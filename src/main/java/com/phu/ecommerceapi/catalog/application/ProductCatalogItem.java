package com.phu.ecommerceapi.catalog.application;

import java.math.BigDecimal;

public record ProductCatalogItem(
        long id,
        String name,
        BigDecimal price,
        String currency,
        int stock
) {
}
