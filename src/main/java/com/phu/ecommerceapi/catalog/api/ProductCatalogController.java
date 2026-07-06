package com.phu.ecommerceapi.catalog.api;

import com.phu.ecommerceapi.catalog.application.PageResponse;
import com.phu.ecommerceapi.catalog.application.ProductCatalogItem;
import com.phu.ecommerceapi.catalog.application.ProductCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;

    public ProductCatalogController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductCatalogItem>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productCatalogService.searchActiveProducts(search, page, size));
    }

    @GetMapping("/getAll")
    public ResponseEntity<PageResponse<ProductCatalogItem>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return getProducts(search, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductCatalogItem> getProduct(@PathVariable long id) {
        return ResponseEntity.ok(productCatalogService.getActiveProduct(id));
    }

    @GetMapping("/getById")
    public ResponseEntity<ProductCatalogItem> getProductByQueryParam(@RequestParam long id) {
        return getProduct(id);
    }
}
