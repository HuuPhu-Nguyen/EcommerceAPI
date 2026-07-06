package com.phu.ecommerceapi.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepo extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByProductIdAndActiveTrue(long productId);

    Page<ProductModel> findByActiveTrue(Pageable pageable);

    Page<ProductModel> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
}
