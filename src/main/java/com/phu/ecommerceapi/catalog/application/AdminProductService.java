package com.phu.ecommerceapi.catalog.application;

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

    private final AdminProductCatalogPort adminProductCatalogPort;
    private final AuditEventRecorder auditEventRecorder;
    private final InventoryReservationService inventoryReservationService;

    public AdminProductService(
            AdminProductCatalogPort adminProductCatalogPort,
            AuditEventRecorder auditEventRecorder,
            InventoryReservationService inventoryReservationService
    ) {
        this.adminProductCatalogPort = adminProductCatalogPort;
        this.auditEventRecorder = auditEventRecorder;
        this.inventoryReservationService = inventoryReservationService;
    }

    @Transactional
    public ProductAdminResponse create(AdminProductCommand command, CurrentUser actor) {
        ProductAdminSnapshot savedProduct = adminProductCatalogPort.create(command);
        InventorySnapshot inventory = inventoryReservationService.initializeInventory(
                savedProduct.id(),
                command.stock()
        );
        recordAudit(actor, "PRODUCT_CREATED", savedProduct);
        return toResponse(savedProduct, inventory);
    }

    @Transactional
    public ProductAdminResponse update(long productId, AdminProductCommand command, CurrentUser actor) {
        ProductAdminSnapshot savedProduct = adminProductCatalogPort.update(productId, command)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        InventorySnapshot inventory = inventoryReservationService.setAvailableQuantity(
                savedProduct.id(),
                command.stock()
        );
        recordAudit(actor, "PRODUCT_UPDATED", savedProduct);
        return toResponse(savedProduct, inventory);
    }

    @Transactional
    public ProductAdminResponse deactivate(long productId, CurrentUser actor) {
        ProductAdminSnapshot savedProduct = adminProductCatalogPort.deactivate(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        recordAudit(actor, "PRODUCT_DEACTIVATED", savedProduct);
        return toResponse(savedProduct, inventoryReservationService.getInventory(savedProduct.id()));
    }

    private void recordAudit(CurrentUser actor, String action, ProductAdminSnapshot product) {
        auditEventRecorder.record(new AuditEventCommand(
                actor.subject(),
                action,
                RESOURCE_TYPE,
                Long.toString(product.id()),
                "name=%s;active=%s".formatted(product.name(), product.active())
        ));
    }

    private ProductAdminResponse toResponse(ProductAdminSnapshot product, InventorySnapshot inventory) {
        return new ProductAdminResponse(
                product.id(),
                product.name(),
                product.price(),
                product.currency(),
                inventory.availableQuantity(),
                product.active()
        );
    }
}
