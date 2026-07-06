package com.phu.ecommerceapi.catalog.api;

import com.phu.ecommerceapi.catalog.application.AdminProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminProductRequest(
        @NotBlank String name,
        @PositiveOrZero double price,
        @PositiveOrZero double stock,
        Boolean active
) {

    public AdminProductCommand toCommand() {
        return new AdminProductCommand(name, price, stock, active);
    }
}
