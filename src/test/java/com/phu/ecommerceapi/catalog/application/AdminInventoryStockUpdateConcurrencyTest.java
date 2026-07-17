package com.phu.ecommerceapi.catalog.application;

import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.catalog.infrastructure.ProductRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRepository;
import com.phu.ecommerceapi.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.phu.ecommerceapi.audit.AuditEventTestCleaner.clearAuditEvents;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AdminInventoryStockUpdateConcurrencyTest {

    @Autowired
    private AdminProductService adminProductService;

    @Autowired
    private ProductCatalogService productCatalogService;

    @Autowired
    private InventoryReservationService inventoryReservationService;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetData() {
        outboxEventRepository.deleteAll();
        clearAuditEvents(jdbcTemplate);
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @Test
    void concurrentAdminStockUpdateAndCheckoutReservationKeepInventoryConsistent() throws Exception {
        ProductModel product = productWithInventory(2, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> adminUpdate = () -> {
            ready.countDown();
            await(start);
            adminProductService.update(
                    product.getProductId(),
                    command("Race Product Updated", 1),
                    admin()
            );
            return true;
        };
        Callable<Boolean> checkoutReservation = () -> {
            ready.countDown();
            await(start);
            inventoryReservationService.reserve(product.getProductId(), 1);
            return true;
        };

        try {
            Future<Boolean> adminResult = executor.submit(adminUpdate);
            Future<Boolean> checkoutResult = executor.submit(checkoutReservation);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    adminResult.get(5, TimeUnit.SECONDS),
                    checkoutResult.get(5, TimeUnit.SECONDS)
            )).containsOnly(true);
        } finally {
            executor.shutdownNow();
        }

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isNotNegative();
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(productCatalogService.getActiveProduct(product.getProductId()).stock())
                .isEqualTo(inventory.getAvailableQuantity());
    }

    private ProductModel productWithInventory(int availableQuantity, int reservedQuantity) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name("Race Product")
                .price(new BigDecimal("10.00"))
                .stock(availableQuantity)
                .active(true)
                .build());
        inventoryRepository.save(new InventoryRecord(product.getProductId(), availableQuantity, reservedQuantity));
        return product;
    }

    private AdminProductCommand command(String name, int availableQuantity) {
        return new AdminProductCommand(
                name,
                Money.of("10.00", "USD"),
                availableQuantity,
                true
        );
    }

    private CurrentUser admin() {
        return new CurrentUser(
                "admin-subject",
                "admin",
                "admin@example.com",
                Set.of("admin"),
                Set.of("product:write")
        );
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent stock update test did not start");
        }
    }
}
