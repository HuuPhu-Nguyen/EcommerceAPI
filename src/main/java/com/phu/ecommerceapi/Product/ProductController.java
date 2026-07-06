package com.phu.ecommerceapi.Product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.phu.ecommerceapi.identity.application.SecurityExpressions.ADMIN_PRODUCT_WRITE;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping("/add")
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductResponse> addProduct(@RequestBody ProductRequest productRequest){
        return ResponseEntity.ok(productService.save(productRequest));
    }

    @DeleteMapping("/delete")
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductResponse> deleteProduct(@RequestBody long productId){
        return ResponseEntity.ok(productService.delete(productId));
    }

}
