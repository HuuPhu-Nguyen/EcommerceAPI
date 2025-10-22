package com.phu.ecommerceapi.CartItem;

import com.phu.ecommerceapi.Cart.CartModel;
import com.phu.ecommerceapi.Product.ProductModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CartItemModel{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne @JoinColumn(name="product_id")
    private ProductModel productModel;

    @ManyToOne @JoinColumn(name="cart_id")
    private CartModel cart;

    private int quantity;

    public long getProductID(){return this.productModel.getProductId();}

    @Override
    public boolean equals(Object o) {
        return o instanceof CartItemModel && this.getProductID()== ((CartItemModel)o).getProductID();
    }
}
