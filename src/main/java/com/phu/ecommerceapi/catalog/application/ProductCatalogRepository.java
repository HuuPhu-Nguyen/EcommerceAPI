package com.phu.ecommerceapi.catalog.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductCatalogRepository {

    Optional<ProductCatalogItem> findActiveById(long id);

    Page<ProductCatalogItem> findActiveProducts(String search, Pageable pageable);
}
