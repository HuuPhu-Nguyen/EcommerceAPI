package com.phu.ecommerceapi.User;

import com.phu.ecommerceapi.customer.application.CustomerProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Autowired
    UserService userService;

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
