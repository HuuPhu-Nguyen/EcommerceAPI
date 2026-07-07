package com.phu.ecommerceapi.catalog.infrastructure;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.catalog.application.ProductCatalogItem;
import com.phu.ecommerceapi.catalog.application.ProductCatalogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaProductCatalogRepository implements ProductCatalogRepository {

    private final ProductRepo productRepo;

    public JpaProductCatalogRepository(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    @Override
    public Optional<ProductCatalogItem> findActiveById(long id) {
        return productRepo.findByProductIdAndActiveTrue(id)
                .map(this::toCatalogItem);
    }

    @Override
    public Page<ProductCatalogItem> findActiveProducts(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return productRepo.findByActiveTrue(pageable)
                    .map(this::toCatalogItem);
        }

        return productRepo.findByActiveTrueAndNameContainingIgnoreCase(search.trim(), pageable)
                .map(this::toCatalogItem);
    }

    private ProductCatalogItem toCatalogItem(ProductModel product) {
        return new ProductCatalogItem(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock()
        );
    }
}
