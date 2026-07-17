package com.phu.ecommerceapi.inventory.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.catalog.infrastructure.ProductRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.outbox.application.OutboxEventProcessor;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRepository;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventStatus;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import org.junit.jupiter.api.AfterEach;
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
    private OutboxEventProcessor outboxEventProcessor;

    @Autowired
    private StockEventBroadcaster stockEventBroadcaster;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetInventory() {
        outboxEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @AfterEach
    void closeStockStreams() {
        stockEventBroadcaster.completeAll();
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
    void reserveWritesStockChangedOutboxEvent() throws Exception {
        long productId = productWithInventory(5);
        outboxEventRepository.deleteAll();

        inventoryReservationService.reserve(productId, 2);

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("PRODUCT");
                    assertThat(event.getAggregateId()).isEqualTo(Long.toString(productId));
                    assertThat(event.getEventType()).isEqualTo("StockChanged");
                    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
                    JsonNode payload = readPayload(event.getPayload());
                    assertThat(payload.get("productId").asLong()).isEqualTo(productId);
                    assertThat(payload.get("availableQuantity").asInt()).isEqualTo(3);
                    assertThat(payload.get("reservedQuantity").asInt()).isEqualTo(2);
                    assertThat(payload.get("reason").asText()).isEqualTo("RESERVED");
                });
    }

    @Test
    void stockChangedOutboxEventCanBeProcessedForSseSubscribers() {
        long productId = productWithInventory(5);
        outboxEventRepository.deleteAll();
        stockEventBroadcaster.subscribe(productId);

        inventoryReservationService.reserve(productId, 2);
        int processed = outboxEventProcessor.processPendingBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(stockEventBroadcaster.subscriberCount(productId)).isEqualTo(1);
        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("StockChanged");
                    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
                    assertThat(event.getProcessedAt()).isNotNull();
                });
    }

    @Test
    void reserveFailsWhenAvailableQuantityIsTooLow() {
        long productId = productWithInventory(1);
        outboxEventRepository.deleteAll();

        assertThatThrownBy(() -> inventoryReservationService.reserve(productId, 2))
                .isInstanceOf(OutOfStockException.class)
                .hasMessage("Not enough stock is available");

        InventorySnapshot inventory = inventoryReservationService.getInventory(productId);
        assertThat(inventory.availableQuantity()).isEqualTo(1);
        assertThat(inventory.reservedQuantity()).isEqualTo(0);
        assertThat(outboxEventRepository.findAll()).isEmpty();
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
                .price(new java.math.BigDecimal("10.00"))
                .active(true)
                .build());
        inventoryReservationService.initializeInventory(product.getProductId(), availableQuantity);
        return product.getProductId();
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new AssertionError("Outbox payload is not valid JSON", exception);
        }
    }
}
