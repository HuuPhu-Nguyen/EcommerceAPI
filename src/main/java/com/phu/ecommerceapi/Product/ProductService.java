package com.phu.ecommerceapi.Product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepo repo;

    public ProductResponse save(ProductRequest product) {
        ProductModel newProduct = ProductModel.builder()
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
        repo.save(newProduct);

        ProductResponse response = ProductResponse.builder()
                .id(newProduct.getProductId())
                .name((newProduct.getName()))
                .price(newProduct.getPrice())
                .stock(newProduct.getStock())
                .build();
        return response;
    }

    public ProductResponse delete(long productId) {
        ProductModel product = repo.findById(productId).orElse(null);
        repo.delete(product);
        return ProductResponse.builder()
                .id(product.getProductId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
     }

    public List<ProductModel> getAll() {
        return repo.findAll();
    }

    public ProductResponse getById(long id) {
        Optional<ProductModel> product = repo.findById(id);
        if(product.isPresent()){
            return ProductResponse.builder()
                    .id(product.get().getProductId())
                    .name(product.get().getName())
                    .price(product.get().getPrice())
                    .stock(product.get().getStock())
                    .build();
        }
        else{
            throw new RuntimeException("Product not found");
        }
    }
}
