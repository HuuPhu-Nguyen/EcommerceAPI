package com.phu.ecommerceapi.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<UserModel, Long> {
    UserModel findByIdentitySubject(String identitySubject);

    UserModel findByUsername(String username);

    UserModel findByEmail(String email);
}
