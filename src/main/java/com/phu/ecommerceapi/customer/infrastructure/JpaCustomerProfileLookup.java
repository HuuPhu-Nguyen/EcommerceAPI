package com.phu.ecommerceapi.customer.infrastructure;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfilePage;
import com.phu.ecommerceapi.customer.application.CustomerProfileLookup;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaCustomerProfileLookup implements CustomerProfileLookup {

    private final UserRepo userRepo;

    public JpaCustomerProfileLookup(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public Optional<CustomerProfile> findCurrentUserProfile(CurrentUser currentUser) {
        UserModel user = userRepo.findByIdentitySubject(currentUser.subject());
        return Optional.ofNullable(user)
                .map(this::toCustomerProfile);
    }

    @Override
    public CustomerProfilePage findProfiles(int page, int size) {
        Page<UserModel> users = userRepo.findAllByOrderByIdAsc(PageRequest.of(page, size));
        List<CustomerProfile> profiles = users.getContent()
                .stream()
                .map(this::toCustomerProfile)
                .toList();
        return new CustomerProfilePage(
                profiles,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
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
