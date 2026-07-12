package com.phu.ecommerceapi.customer.infrastructure;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfileProvisioningPort;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaCustomerProfileProvisioningAdapter implements CustomerProfileProvisioningPort {

    private final UserRepo userRepo;

    public JpaCustomerProfileProvisioningAdapter(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public CustomerProfile provisionCurrentProfile(CurrentUser currentUser) {
        UserModel existingUser = userRepo.findByIdentitySubject(currentUser.subject());
        if (existingUser != null) {
            return toCustomerProfile(existingUser);
        }

        userRepo.insertProvisionedProfileIfAbsent(
                currentUser.subject(),
                currentUser.username(),
                currentUser.email()
        );

        UserModel provisionedUser = userRepo.findByIdentitySubject(currentUser.subject());
        if (provisionedUser == null) {
            throw new IllegalStateException("Customer profile provisioning did not create or find a profile");
        }
        return toCustomerProfile(provisionedUser);
    }

    private CustomerProfile toCustomerProfile(UserModel user) {
        return CustomerProfile.fromUserRecord(
                user.getId(),
                user.getIdentitySubject(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }
}
