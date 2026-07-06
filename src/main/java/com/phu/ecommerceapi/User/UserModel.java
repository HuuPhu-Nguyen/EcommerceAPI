package com.phu.ecommerceapi.User;

import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;

    @OneToMany(mappedBy = "owner")
    private List<CartModel> carts;
}
