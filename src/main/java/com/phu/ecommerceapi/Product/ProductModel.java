package com.phu.ecommerceapi.Product;

import com.phu.ecommerceapi.cart.infrastructure.CartItemModel;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProductModel {

    @EqualsAndHashCode.Include
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long productId;

    @EqualsAndHashCode.Include
    private String name;

    private double price;

    private double stock;

    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "productModel",cascade = CascadeType.ALL)
    private List<CartItemModel> cartItems;

}
