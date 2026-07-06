package com.phu.ecommerceapi.User;

import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    @Autowired
    UserService userService;

    @PostMapping("/register")
    public UserModel registerUser(@RequestBody UserModel user) {
        return userService.save(user);
    }

    @GetMapping("/user")
    @PreAuthorize(SecurityExpressions.CUSTOMER_PROFILE_READ)
    public UserModel getUser(@AuthenticatedUser CurrentUser currentUser) {
        return userService.findCurrentUserProfile(currentUser);
    }

    @GetMapping("/allUserInfo")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_USER_READ)
    public List<UserModel> getAllUser() {
        return userService.findAll();
    }


}
