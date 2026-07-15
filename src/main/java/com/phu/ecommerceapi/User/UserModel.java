package com.phu.ecommerceapi.User;

import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String identitySubject;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;

    @OneToMany(mappedBy = "owner")
    private List<CartModel> carts;

    public static UserModel reference(long id, String identitySubject) {
        UserModel user = new UserModel();
        user.id = id;
        user.identitySubject = identitySubject;
        return user;
    }

    public long getId() {
        return id;
    }

    public String getIdentitySubject() {
        return identitySubject;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }
}
