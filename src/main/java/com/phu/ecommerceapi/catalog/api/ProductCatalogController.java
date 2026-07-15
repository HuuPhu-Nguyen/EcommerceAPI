package com.phu.ecommerceapi.catalog.api;

import com.phu.ecommerceapi.catalog.application.PageResponse;
import com.phu.ecommerceapi.catalog.application.ProductCatalogItem;
import com.phu.ecommerceapi.catalog.application.ProductCatalogService;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize(SecurityExpressions.PRODUCT_READ)
    public ResponseEntity<PageResponse<ProductCatalogItem>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productCatalogService.searchActiveProducts(search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.PRODUCT_READ)
    public ResponseEntity<ProductCatalogItem> getProduct(@PathVariable long id) {
        return ResponseEntity.ok(productCatalogService.getActiveProduct(id));
    }
}
