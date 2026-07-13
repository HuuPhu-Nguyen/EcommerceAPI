package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.shared.domain.Money;

import java.util.List;
import java.util.Objects;

public record CartSnapshot(
        long id,
        long ownerId,
        String ownerIdentitySubject,
        Money total,
        String currency,
        List<CartItemSnapshot> items
) {

    public CartSnapshot {
        Objects.requireNonNull(ownerIdentitySubject, "cart owner identity subject is required");
        Objects.requireNonNull(total, "cart total is required");
        Objects.requireNonNull(currency, "cart currency is required");
        items = List.copyOf(Objects.requireNonNull(items, "cart items are required"));
    }

    public boolean belongsToIdentitySubject(String identitySubject) {
        return Objects.equals(ownerIdentitySubject, identitySubject);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
