package com.phu.ecommerceapi.Cart;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.CartItem.CartItemModel;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
public class CartModel {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private double total;

    @OneToMany(mappedBy = "cart", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<CartItemModel> items;

    @ManyToOne() @JoinColumn(name = "owner_id")
    private UserModel owner;
}
