package com.phu.ecommerceapi.User;

import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepo repo;
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public UserModel save(UserModel user) {
        user.setPassword(encoder.encode(user.getPassword()));
        return repo.save(user);
    }

    public UserModel findByUsername(String username) {
        return repo.findByUsername(username);
    }

    public UserModel findCurrentUserProfile(CurrentUser currentUser) {
        UserModel user = repo.findByUsername(currentUser.username());
        if (user == null && currentUser.email() != null) {
            user = repo.findByEmail(currentUser.email());
        }
        if (user == null) {
            throw new NotFoundException("User profile not found");
        }
        return user;
    }

    public List<UserModel> findAll() {
        return repo.findAll();
    }
}
