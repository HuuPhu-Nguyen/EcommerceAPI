package com.phu.ecommerceapi.catalog.application;

import java.math.BigDecimal;

public record ProductAdminResponse(
        long id,
        String name,
        BigDecimal price,
        String currency,
        int stock,
        boolean active
) {
}
