package com.phu.ecommerceapi.catalog.api;

import com.phu.ecommerceapi.catalog.application.AdminProductService;
import com.phu.ecommerceapi.catalog.application.ProductAdminResponse;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.phu.ecommerceapi.identity.application.SecurityExpressions.ADMIN_PRODUCT_WRITE;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @PostMapping
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductAdminResponse> createProduct(
            @Valid @RequestBody AdminProductRequest request,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(adminProductService.create(request.toCommand(), currentUser));
    }

    @PutMapping("/{productId}")
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductAdminResponse> updateProduct(
            @PathVariable long productId,
            @Valid @RequestBody AdminProductRequest request,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(adminProductService.update(productId, request.toCommand(), currentUser));
    }

    @PostMapping("/{productId}/deactivate")
    @PreAuthorize(ADMIN_PRODUCT_WRITE)
    public ResponseEntity<ProductAdminResponse> deactivateProduct(
            @PathVariable long productId,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(adminProductService.deactivate(productId, currentUser));
    }
}
