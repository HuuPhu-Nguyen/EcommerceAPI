package com.phu.ecommerceapi.catalog.api;

import com.phu.ecommerceapi.catalog.application.AdminProductCommand;
import com.phu.ecommerceapi.shared.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

public record AdminProductRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @Size(min = 3, max = 3) String currency,
        @PositiveOrZero int stock,
        Boolean active
) {

    public AdminProductRequest {
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
    }

    public AdminProductCommand toCommand() {
        return new AdminProductCommand(name, Money.of(price, currency), stock, active);
    }
}
