package com.phu.ecommerceapi.catalog.infrastructure;

import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.catalog.application.CartProductLookupPort;
import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaCartProductLookupAdapter implements CartProductLookupPort {

    private final ProductRepo productRepo;

    public JpaCartProductLookupAdapter(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    @Override
    public Optional<CartProductSnapshot> findActiveForCart(long productId) {
        return productRepo.findActiveCartProductById(productId);
    }
}
