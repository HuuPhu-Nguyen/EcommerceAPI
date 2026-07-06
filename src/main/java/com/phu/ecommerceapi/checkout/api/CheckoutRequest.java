package com.phu.ecommerceapi.checkout.api;

import jakarta.validation.constraints.Positive;

public record CheckoutRequest(@Positive long cartId) {
}
