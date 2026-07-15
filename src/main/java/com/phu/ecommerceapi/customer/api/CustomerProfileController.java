package com.phu.ecommerceapi.customer.api;

import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfileService;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerProfileController {

    private static final int DEFAULT_PROFILE_PAGE = 0;
    private static final int DEFAULT_PROFILE_PAGE_SIZE = 50;
    private static final int MAX_PROFILE_PAGE_SIZE = 100;

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/customer/profile/me")
    @PreAuthorize(SecurityExpressions.CUSTOMER_PROFILE_READ)
    public CustomerProfile getCurrentProfile(@AuthenticatedUser CurrentUser currentUser) {
        return customerProfileService.getCurrentProfile(currentUser);
    }

    @PostMapping("/customer/profile/me")
    @PreAuthorize(SecurityExpressions.CUSTOMER_PROFILE_WRITE)
    public CustomerProfile provisionCurrentProfile(@AuthenticatedUser CurrentUser currentUser) {
        return customerProfileService.provisionCurrentProfile(currentUser);
    }

    @GetMapping("/admin/customer-profiles")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_USER_READ)
    public CustomerProfilePageResponse getAllProfiles(
            @RequestParam(defaultValue = "" + DEFAULT_PROFILE_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PROFILE_PAGE_SIZE) int size
    ) {
        validatePageRequest(page, size);
        return CustomerProfilePageResponse.from(customerProfileService.getProfiles(page, size));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PROFILE_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PROFILE_PAGE_SIZE);
        }
    }
}
