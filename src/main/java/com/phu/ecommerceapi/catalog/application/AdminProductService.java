package com.phu.ecommerceapi.catalog.application;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProductService {

    private static final String RESOURCE_TYPE = "PRODUCT";

    private final ProductRepo productRepo;
    private final AuditEventRecorder auditEventRecorder;

    public AdminProductService(ProductRepo productRepo, AuditEventRecorder auditEventRecorder) {
        this.productRepo = productRepo;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public ProductAdminResponse create(AdminProductCommand command, CurrentUser actor) {
        ProductModel product = ProductModel.builder()
                .name(command.name())
                .price(command.price())
                .stock(command.stock())
                .active(command.activeOrDefault(true))
                .build();

        ProductModel savedProduct = productRepo.save(product);
        recordAudit(actor, "PRODUCT_CREATED", savedProduct);
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductAdminResponse update(long productId, AdminProductCommand command, CurrentUser actor) {
        ProductModel product = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        product.setName(command.name());
        product.setPrice(command.price());
        product.setStock(command.stock());
        product.setActive(command.activeOrDefault(product.isActive()));

        ProductModel savedProduct = productRepo.save(product);
        recordAudit(actor, "PRODUCT_UPDATED", savedProduct);
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductAdminResponse deactivate(long productId, CurrentUser actor) {
        ProductModel product = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        product.setActive(false);
        ProductModel savedProduct = productRepo.save(product);
        recordAudit(actor, "PRODUCT_DEACTIVATED", savedProduct);
        return toResponse(savedProduct);
    }

    private void recordAudit(CurrentUser actor, String action, ProductModel product) {
        auditEventRecorder.record(new AuditEventCommand(
                actor.subject(),
                action,
                RESOURCE_TYPE,
                Long.toString(product.getProductId()),
                "name=%s;active=%s".formatted(product.getName(), product.isActive())
        ));
    }

    private ProductAdminResponse toResponse(ProductModel product) {
        return new ProductAdminResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.isActive()
        );
    }
}
