package com.phu.ecommerceapi.Cart;

import lombok.Data;

@Data
public class CartDTO {
    private long cartID;
    private long productID;
    private int quantity;

}


