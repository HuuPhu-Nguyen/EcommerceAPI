package com.phu.ecommerceapi.inventory.application;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InventoryReservationServiceTest {

    @Autowired
    private InventoryReservationService inventoryReservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepo productRepo;

    @BeforeEach
    void resetInventory() {
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @Test
    void reserveMovesQuantityFromAvailableToReserved() {
        long productId = productWithInventory(5);

        inventoryReservationService.reserve(productId, 2);

        InventorySnapshot inventory = inventoryReservationService.getInventory(productId);
        assertThat(inventory.availableQuantity()).isEqualTo(3);
        assertThat(inventory.reservedQuantity()).isEqualTo(2);
    }

    @Test
    void reserveFailsWhenAvailableQuantityIsTooLow() {
        long productId = productWithInventory(1);

        assertThatThrownBy(() -> inventoryReservationService.reserve(productId, 2))
                .isInstanceOf(OutOfStockException.class)
                .hasMessage("Not enough stock is available");

        InventorySnapshot inventory = inventoryReservationService.getInventory(productId);
        assertThat(inventory.availableQuantity()).isEqualTo(1);
        assertThat(inventory.reservedQuantity()).isEqualTo(0);
    }

    @Test
    void concurrentReservationsCannotOversellSingleAvailableUnit() throws Exception {
        long productId = productWithInventory(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> reservationAttempt = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                inventoryReservationService.reserve(productId, 1);
                return true;
            } catch (OutOfStockException exception) {
                return false;
            }
        };

        try {
            Future<Boolean> firstAttempt = executor.submit(reservationAttempt);
            Future<Boolean> secondAttempt = executor.submit(reservationAttempt);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = List.of(firstAttempt.get(), secondAttempt.get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        InventorySnapshot inventory = inventoryReservationService.getInventory(productId);
        assertThat(inventory.availableQuantity()).isZero();
        assertThat(inventory.reservedQuantity()).isEqualTo(1);
    }

    private long productWithInventory(int availableQuantity) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name("Inventory Product")
                .price(10)
                .stock(availableQuantity)
                .active(true)
                .build());
        inventoryReservationService.initializeInventory(product.getProductId(), availableQuantity);
        return product.getProductId();
    }
}
