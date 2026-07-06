package com.phu.ecommerceapi.customer.application;

import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerProfileService {

    private final CustomerProfileLookup customerProfileLookup;

    public CustomerProfileService(CustomerProfileLookup customerProfileLookup) {
        this.customerProfileLookup = customerProfileLookup;
    }

    public CustomerProfile getCurrentProfile(CurrentUser currentUser) {
        return customerProfileLookup.findCurrentUserProfile(currentUser)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
    }

    public List<CustomerProfile> getAllProfiles() {
        return customerProfileLookup.findAllProfiles();
    }
}
