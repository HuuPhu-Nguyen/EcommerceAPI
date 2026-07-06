package com.phu.ecommerceapi.customer.application;

import com.phu.ecommerceapi.identity.application.CurrentUser;

import java.util.List;
import java.util.Optional;

public interface CustomerProfileLookup {

    Optional<CustomerProfile> findCurrentUserProfile(CurrentUser currentUser);

    List<CustomerProfile> findAllProfiles();
}
