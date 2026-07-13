package com.phu.ecommerceapi.catalog.application;

import java.util.Optional;

public interface AdminProductCatalogPort {

    ProductAdminSnapshot create(AdminProductCommand command);

    Optional<ProductAdminSnapshot> update(long productId, AdminProductCommand command);

    Optional<ProductAdminSnapshot> deactivate(long productId);
}
