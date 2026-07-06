package com.phu.ecommerceapi.customer.api;

import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfileService;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping({"/customer/profile/me", "/user"})
    @PreAuthorize(SecurityExpressions.CUSTOMER_PROFILE_READ)
    public CustomerProfile getCurrentProfile(@AuthenticatedUser CurrentUser currentUser) {
        return customerProfileService.getCurrentProfile(currentUser);
    }

    @GetMapping({"/admin/customer-profiles", "/allUserInfo"})
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_USER_READ)
    public List<CustomerProfile> getAllProfiles() {
        return customerProfileService.getAllProfiles();
    }
}
