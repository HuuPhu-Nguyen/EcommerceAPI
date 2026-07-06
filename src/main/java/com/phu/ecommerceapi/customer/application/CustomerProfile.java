package com.phu.ecommerceapi.customer.application;

public record CustomerProfile(
        Long customerId,
        String identitySubject,
        String username,
        String firstName,
        String lastName,
        String email
) {

    public static CustomerProfile fromUserRecord(
            Long customerId,
            String identitySubject,
            String username,
            String firstName,
            String lastName,
            String email
    ) {
        return new CustomerProfile(customerId, identitySubject, username, firstName, lastName, email);
    }
}
