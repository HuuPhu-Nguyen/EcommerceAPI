package com.phu.ecommerceapi.customer.application;

import java.util.Objects;

public record CustomerIdentity(
        long id,
        String identitySubject
) {

    public CustomerIdentity {
        Objects.requireNonNull(identitySubject, "customer identity subject is required");
    }
}
