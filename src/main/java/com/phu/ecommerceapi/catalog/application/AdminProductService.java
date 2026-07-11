package com.phu.ecommerceapi.catalog.application;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.inventory.application.InventorySnapshot;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProductService {

    private static final String RESOURCE_TYPE = "PRODUCT";

    private final ProductRepo productRepo;
    private final AuditEventRecorder auditEventRecorder;
    private final InventoryReservationService inventoryReservationService;

    public AdminProductService(
            ProductRepo productRepo,
            AuditEventRecorder auditEventRecorder,
            InventoryReservationService inventoryReservationService
    ) {
        this.productRepo = productRepo;
        this.auditEventRecorder = auditEventRecorder;
        this.inventoryReservationService = inventoryReservationService;
    }

    @Transactional
    public ProductAdminResponse create(AdminProductCommand command, CurrentUser actor) {
        ProductModel product = ProductModel.builder()
                .name(command.name())
                .price(command.price().amount())
                .currency(command.price().currency().getCurrencyCode())
                .stock(command.stock())
                .active(command.activeOrDefault(true))
                .build();

        ProductModel savedProduct = productRepo.save(product);
        InventorySnapshot inventory = inventoryReservationService.initializeInventory(
                savedProduct.getProductId(),
                command.stock()
        );
        savedProduct.setStock(inventory.availableQuantity());
        recordAudit(actor, "PRODUCT_CREATED", savedProduct);
        return toResponse(savedProduct, inventory);
    }

    @Transactional
    public ProductAdminResponse update(long productId, AdminProductCommand command, CurrentUser actor) {
        ProductModel product = productRepo.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        product.setName(command.name());
        product.setPrice(command.price());
        product.setActive(command.activeOrDefault(product.isActive()));

        InventorySnapshot inventory = inventoryReservationService.setAvailableQuantity(
                product.getProductId(),
                command.stock()
        );
        product.setStock(inventory.availableQuantity());
        ProductModel savedProduct = productRepo.save(product);
        recordAudit(actor, "PRODUCT_UPDATED", savedProduct);
        return toResponse(savedProduct, inventory);
    }

    @Transactional
    public ProductAdminResponse deactivate(long productId, CurrentUser actor) {
        ProductModel product = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        product.setActive(false);
        ProductModel savedProduct = productRepo.save(product);
        recordAudit(actor, "PRODUCT_DEACTIVATED", savedProduct);
        return toResponse(savedProduct, inventoryReservationService.getInventory(savedProduct.getProductId()));
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

    private ProductAdminResponse toResponse(ProductModel product, InventorySnapshot inventory) {
        return new ProductAdminResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                inventory.availableQuantity(),
                product.isActive()
        );
    }
}
