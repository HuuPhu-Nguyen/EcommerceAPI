package com.phu.ecommerceapi.customer.infrastructure;

import com.phu.ecommerceapi.customer.application.CustomerIdentity;
import com.phu.ecommerceapi.customer.application.CustomerIdentityLookupPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaCustomerIdentityLookupAdapter implements CustomerIdentityLookupPort {

    private final UserRepo userRepo;

    public JpaCustomerIdentityLookupAdapter(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public Optional<CustomerIdentity> findByIdentitySubject(String identitySubject) {
        return Optional.ofNullable(userRepo.findByIdentitySubject(identitySubject))
                .map(this::toIdentity);
    }

    private CustomerIdentity toIdentity(UserModel user) {
        return new CustomerIdentity(user.getId(), user.getIdentitySubject());
    }
}
