package com.phu.ecommerceapi.cart.api;

import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @Positive long productId,
        @Positive int quantity
) {
}
