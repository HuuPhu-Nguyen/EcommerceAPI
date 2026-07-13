package com.phu.ecommerceapi.Product;

import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;
import com.phu.ecommerceapi.catalog.application.ProductCatalogItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepo extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByProductIdAndActiveTrue(long productId);

    @Query(
            value = """
                    select *
                    from product_model
                    where product_id = :productId
                    for update
                    """,
            nativeQuery = true
    )
    Optional<ProductModel> findByIdForUpdate(@Param("productId") long productId);

    Page<ProductModel> findByActiveTrue(Pageable pageable);

    Page<ProductModel> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            select new com.phu.ecommerceapi.catalog.application.CartProductSnapshot(
                product.productId,
                product.name,
                product.price,
                product.currency,
                product.active
            )
            from ProductModel product
            where product.productId = :productId
              and product.active = true
            """)
    Optional<CartProductSnapshot> findActiveCartProductById(@Param("productId") long productId);

    @Query("""
            select new com.phu.ecommerceapi.catalog.application.ProductCatalogItem(
                product.productId,
                product.name,
                product.price,
                product.currency,
                coalesce(inventory.availableQuantity, product.stock)
            )
            from ProductModel product
            left join InventoryRecord inventory on inventory.productId = product.productId
            where product.productId = :productId
              and product.active = true
            """)
    Optional<ProductCatalogItem> findActiveCatalogItemById(@Param("productId") long productId);

    @Query(
            value = """
                    select new com.phu.ecommerceapi.catalog.application.ProductCatalogItem(
                        product.productId,
                        product.name,
                        product.price,
                        product.currency,
                        coalesce(inventory.availableQuantity, product.stock)
                    )
                    from ProductModel product
                    left join InventoryRecord inventory on inventory.productId = product.productId
                    where product.active = true
                    """,
            countQuery = """
                    select count(product)
                    from ProductModel product
                    where product.active = true
                    """
    )
    Page<ProductCatalogItem> findActiveCatalogItems(Pageable pageable);

    @Query(
            value = """
                    select new com.phu.ecommerceapi.catalog.application.ProductCatalogItem(
                        product.productId,
                        product.name,
                        product.price,
                        product.currency,
                        coalesce(inventory.availableQuantity, product.stock)
                    )
                    from ProductModel product
                    left join InventoryRecord inventory on inventory.productId = product.productId
                    where product.active = true
                      and lower(product.name) like lower(concat('%', :name, '%'))
                    """,
            countQuery = """
                    select count(product)
                    from ProductModel product
                    where product.active = true
                      and lower(product.name) like lower(concat('%', :name, '%'))
                    """
    )
    Page<ProductCatalogItem> findActiveCatalogItemsByName(@Param("name") String name, Pageable pageable);
}
