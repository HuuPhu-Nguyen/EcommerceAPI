package com.phu.ecommerceapi.Product;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductResponse {
    private long id;
    private String name;
    private double price;
    private double stock;
    private boolean active;
}
