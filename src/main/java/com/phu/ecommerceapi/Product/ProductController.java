package com.phu.ecommerceapi.Product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/getById")
    public ResponseEntity<ProductResponse> getProduct(@RequestParam long id){
        return ResponseEntity.ok(productService.getById(id));
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<ProductModel>> getAllProduct(){
        return ResponseEntity.ok(productService.getAll());
    }

    @PostMapping("/add")
    public ResponseEntity<ProductResponse> addProduct(@RequestBody ProductRequest productRequest){
        return ResponseEntity.ok(productService.save(productRequest));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ProductResponse> deleteProduct(@RequestBody long productId){
        return ResponseEntity.ok(productService.delete(productId));
    }

}
