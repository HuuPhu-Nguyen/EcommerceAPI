package com.phu.ecommerceapi.User;

import com.phu.ecommerceapi.Security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    @Autowired
    UserService userService;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/register")
    public UserModel registerUser(@RequestBody UserModel user) {
        return userService.save(user);
    }

    @PostMapping("/login")
    public String loginUser(@RequestBody LoginDTO user) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
        );
        if (authentication.isAuthenticated()) {
            return jwtUtils.generateToken(user.getUsername());
        }
        else{
            return "failed";
        }
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
