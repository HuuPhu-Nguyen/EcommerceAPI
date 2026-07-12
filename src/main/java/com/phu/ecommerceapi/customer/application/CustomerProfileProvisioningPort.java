package com.phu.ecommerceapi.customer.application;

import com.phu.ecommerceapi.identity.application.CurrentUser;

public interface CustomerProfileProvisioningPort {

    CustomerProfile provisionCurrentProfile(CurrentUser currentUser);
}
