package com.phu.ecommerceapi.Product;

import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
                .active(product.getActive() == null || product.getActive())
                .build();
        repo.save(newProduct);

        return toResponse(newProduct);
    }

    public ProductResponse delete(long productId) {
        ProductModel product = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        product.setActive(false);
        return toResponse(repo.save(product));
    }

    public ProductResponse getById(long id) {
        Optional<ProductModel> product = repo.findByProductIdAndActiveTrue(id);
        if(product.isPresent()){
            return toResponse(product.get());
        }
        else{
            throw new NotFoundException("Product not found");
        }
    }

    private ProductResponse toResponse(ProductModel product) {
        return ProductResponse.builder()
                .id(product.getProductId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .active(product.isActive())
                .build();
    }
}
