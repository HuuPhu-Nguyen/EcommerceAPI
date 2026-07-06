package com.phu.ecommerceapi.Product;

import com.phu.ecommerceapi.catalog.application.AdminProductCommand;
import com.phu.ecommerceapi.catalog.application.AdminProductService;
import com.phu.ecommerceapi.catalog.application.ProductAdminResponse;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.phu.ecommerceapi.identity.application.SecurityExpressions.ADMIN_PRODUCT_WRITE;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final AdminProductService adminProductService;

    public ProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @PostMapping("/add")
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductAdminResponse> addProduct(
            @RequestBody ProductRequest productRequest,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        AdminProductCommand command = new AdminProductCommand(
                productRequest.getName(),
                productRequest.getPrice(),
                (int) productRequest.getStock(),
                productRequest.getActive()
        );
        return ResponseEntity.ok(adminProductService.create(command, currentUser));
    }

    @DeleteMapping("/delete")
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductAdminResponse> deleteProduct(
            @RequestBody long productId,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(adminProductService.deactivate(productId, currentUser));
    }

}
