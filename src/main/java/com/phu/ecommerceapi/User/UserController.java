package com.phu.ecommerceapi.User;

import org.springframework.beans.factory.annotation.Autowired;

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
    public UserModel getUser(@RequestBody String username) {
        return userService.findByUsername(username);
    }

    @GetMapping("/allUserInfo")
    public List<UserModel> getAllUser() {
        return userService.findAll();
    }


}
