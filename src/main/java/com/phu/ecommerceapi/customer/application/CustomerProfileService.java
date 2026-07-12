package com.phu.ecommerceapi.customer.application;

import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerProfileService {

    private final CustomerProfileLookup customerProfileLookup;
    private final CustomerProfileProvisioningPort customerProfileProvisioningPort;

    public CustomerProfileService(
            CustomerProfileLookup customerProfileLookup,
            CustomerProfileProvisioningPort customerProfileProvisioningPort
    ) {
        this.customerProfileLookup = customerProfileLookup;
        this.customerProfileProvisioningPort = customerProfileProvisioningPort;
    }

    public CustomerProfile getCurrentProfile(CurrentUser currentUser) {
        return customerProfileLookup.findCurrentUserProfile(currentUser)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
    }

    public CustomerProfile provisionCurrentProfile(CurrentUser currentUser) {
        return customerProfileProvisioningPort.provisionCurrentProfile(currentUser);
    }

    public List<CustomerProfile> getAllProfiles() {
        return customerProfileLookup.findAllProfiles();
    }
}
