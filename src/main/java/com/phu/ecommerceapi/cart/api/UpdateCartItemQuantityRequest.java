package com.phu.ecommerceapi.cart.api;

import jakarta.validation.constraints.Positive;

public record UpdateCartItemQuantityRequest(@Positive int quantity) {
}
