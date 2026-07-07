package com.phu.ecommerceapi.customer.api;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserService;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerRegistrationController {

    private final UserService userService;

    public CustomerRegistrationController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public CustomerProfile registerUser(@RequestBody UserModel user) {
        UserModel savedUser = userService.save(user);
        return CustomerProfile.fromUserRecord(
                savedUser.getId(),
                null,
                savedUser.getUsername(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getEmail()
        );
    }
}
