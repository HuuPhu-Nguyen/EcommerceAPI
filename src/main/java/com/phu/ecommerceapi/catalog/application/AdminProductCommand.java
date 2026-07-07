package com.phu.ecommerceapi.catalog.application;

import com.phu.ecommerceapi.shared.domain.Money;

public record AdminProductCommand(
        String name,
        Money price,
        int stock,
        Boolean active
) {

    public boolean activeOrDefault(boolean defaultValue) {
        return active == null ? defaultValue : active;
    }
}
