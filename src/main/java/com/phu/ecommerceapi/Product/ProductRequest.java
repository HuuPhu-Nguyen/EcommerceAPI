package com.phu.ecommerceapi.Product;

import lombok.Data;

@Data
public class ProductRequest {
    private String name;
    private double price;
    private double stock;
}
