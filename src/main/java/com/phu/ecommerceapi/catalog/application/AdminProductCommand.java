package com.phu.ecommerceapi.catalog.application;

public record AdminProductCommand(
        String name,
        double price,
        double stock,
        Boolean active
) {

    public boolean activeOrDefault(boolean defaultValue) {
        return active == null ? defaultValue : active;
    }
}
