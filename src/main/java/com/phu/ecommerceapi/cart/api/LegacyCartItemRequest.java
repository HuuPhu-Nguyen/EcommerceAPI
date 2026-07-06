package com.phu.ecommerceapi.cart.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Positive;

public record LegacyCartItemRequest(
        @JsonAlias("cartID") @Positive long cartId,
        @JsonAlias("productID") @Positive long productId,
        @Positive int quantity
) {
}
