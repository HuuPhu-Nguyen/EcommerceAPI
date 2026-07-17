package com.phu.ecommerceapi.catalog.infrastructure;

import com.phu.ecommerceapi.catalog.application.AdminProductCatalogPort;
import com.phu.ecommerceapi.catalog.application.AdminProductCommand;
import com.phu.ecommerceapi.catalog.application.ProductAdminSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaAdminProductCatalogAdapter implements AdminProductCatalogPort {

    private final ProductRepo productRepo;

    public JpaAdminProductCatalogAdapter(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    @Override
    public ProductAdminSnapshot create(AdminProductCommand command) {
        ProductModel product = ProductModel.builder()
                .name(command.name())
                .price(command.price().amount())
                .currency(command.price().currency().getCurrencyCode())
                .active(command.activeOrDefault(true))
                .build();

        return toSnapshot(productRepo.save(product));
    }

    @Override
    public Optional<ProductAdminSnapshot> update(long productId, AdminProductCommand command) {
        return productRepo.findByIdForUpdate(productId)
                .map(product -> {
                    product.setName(command.name());
                    product.setPrice(command.price());
                    product.setActive(command.activeOrDefault(product.isActive()));
                    return toSnapshot(product);
                });
    }

    @Override
    public Optional<ProductAdminSnapshot> deactivate(long productId) {
        return productRepo.findByIdForUpdate(productId)
                .map(product -> {
                    product.setActive(false);
                    return toSnapshot(product);
                });
    }

    private ProductAdminSnapshot toSnapshot(ProductModel product) {
        return new ProductAdminSnapshot(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.isActive()
        );
    }
}
