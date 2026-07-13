package com.phu.ecommerceapi.customer.application;

import java.util.Optional;

public interface CustomerIdentityLookupPort {

    Optional<CustomerIdentity> findByIdentitySubject(String identitySubject);
}
