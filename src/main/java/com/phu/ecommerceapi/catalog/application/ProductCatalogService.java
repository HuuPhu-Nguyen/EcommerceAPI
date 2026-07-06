package com.phu.ecommerceapi.catalog.application;

import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductCatalogRepository productCatalogRepository;

    public ProductCatalogService(ProductCatalogRepository productCatalogRepository) {
        this.productCatalogRepository = productCatalogRepository;
    }

    public ProductCatalogItem getActiveProduct(long id) {
        return productCatalogRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public PageResponse<ProductCatalogItem> searchActiveProducts(String search, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by("name").ascending().and(Sort.by("productId").ascending())
        );
        Page<ProductCatalogItem> productPage = productCatalogRepository.findActiveProducts(search, pageRequest);

        return new PageResponse<>(
                productPage.getContent(),
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages()
        );
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
