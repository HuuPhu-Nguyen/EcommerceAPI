package com.phu.ecommerceapi.customer.api;

import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfilePage;

import java.util.List;
import java.util.Objects;

public record CustomerProfilePageResponse(
        List<CustomerProfile> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public CustomerProfilePageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "customer profile response items are required"));
    }

    public static CustomerProfilePageResponse from(CustomerProfilePage profilePage) {
        return new CustomerProfilePageResponse(
                profilePage.items(),
                profilePage.page(),
                profilePage.size(),
                profilePage.totalElements(),
                profilePage.totalPages()
        );
    }
}
