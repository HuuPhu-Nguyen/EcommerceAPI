package com.phu.ecommerceapi.checkout.api;

import com.jayway.jsonpath.JsonPath;
import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutUseCaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationService inventoryReservationService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetData() {
        auditEventRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
        userRepo.deleteAll();
    }

    @AfterEach
    void cleanUpData() {
        resetData();
    }

    @Test
    void checkoutCreatesPendingOrderReservesInventoryClearsCartAndAudits() throws Exception {
        String username = "checkout-customer@example.com";
        user(username);
        ProductModel product = product("Mechanical Keyboard", 5);
        long cartId = createCart(username);
        addItem(username, cartId, product.getProductId(), 2);

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(username, "checkout:write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.cartId").value(cartId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.allowedPaymentProviders.length()").value(1))
                .andExpect(jsonPath("$.allowedPaymentProviders[0]").value("fake"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(product.getProductId()))
                .andExpect(jsonPath("$.items[0].productName").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].currency").value("USD"));

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(auditEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getAction()).isEqualTo("CHECKOUT_ORDER_CREATED");
                    assertThat(event.getResourceType()).isEqualTo("ORDER");
                    assertThat(event.getActorSubject()).isEqualTo(username);
                });

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt(username, "cart:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0.00));
    }

    @Test
    void failedCheckoutRollsBackPartialReservationAndLeavesCartIntact() throws Exception {
        String username = "rollback-customer@example.com";
        user(username);
        ProductModel availableProduct = product("Available Keyboard", 5);
        ProductModel contestedProduct = product("Contested Headphones", 1);
        long cartId = createCart(username);
        addItem(username, cartId, availableProduct.getProductId(), 2);
        addItem(username, cartId, contestedProduct.getProductId(), 1);

        inventoryReservationService.reserve(contestedProduct.getProductId(), 1);

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(username, "checkout:write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

        InventoryRecord availableInventory = inventoryRepository.findById(availableProduct.getProductId())
                .orElseThrow();
        InventoryRecord contestedInventory = inventoryRepository.findById(contestedProduct.getProductId())
                .orElseThrow();
        assertThat(availableInventory.getAvailableQuantity()).isEqualTo(5);
        assertThat(availableInventory.getReservedQuantity()).isEqualTo(0);
        assertThat(contestedInventory.getAvailableQuantity()).isEqualTo(0);
        assertThat(contestedInventory.getReservedQuantity()).isEqualTo(1);
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(auditEventRepository.findAll()).isEmpty();

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt(username, "cart:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(30.00));
    }

    @Test
    void crossCustomerCheckoutIsDeniedAndDoesNotReserveInventory() throws Exception {
        String owner = "owner@example.com";
        String attacker = "attacker@example.com";
        user(owner);
        user(attacker);
        ProductModel product = product("Owned Cart Product", 4);
        long cartId = createCart(owner);
        addItem(owner, cartId, product.getProductId(), 2);

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(attacker, "checkout:write")))
                .andExpect(status().isForbidden());

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(4);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void concurrentAddItemWhileCheckoutLocksCartReturnsConflictAndCheckoutClearsCart() throws Exception {
        assertConcurrentMutationWhileCheckoutLocksCartConflicts(this::addItemStatus);
    }

    @Test
    void concurrentUpdateItemWhileCheckoutLocksCartReturnsConflictAndCheckoutClearsCart() throws Exception {
        assertConcurrentMutationWhileCheckoutLocksCartConflicts(this::updateItemStatus);
    }

    @Test
    void concurrentRemoveItemWhileCheckoutLocksCartReturnsConflictAndCheckoutClearsCart() throws Exception {
        assertConcurrentMutationWhileCheckoutLocksCartConflicts(this::removeItemStatus);
    }

    @Test
    void checkoutFailsBeforeInventoryReservationWhenNoProviderSupportsCurrency() throws Exception {
        String username = "unsupported-currency-customer@example.com";
        user(username);
        ProductModel product = product("Euro Keyboard", 5, "10.00", "EUR");
        long cartId = createCart(username);
        addItem(username, cartId, product.getProductId(), 2);

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(username, "checkout:write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("No enabled payment provider is available for this order"));

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(auditEventRepository.findAll()).isEmpty();

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt(username, "cart:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void checkoutFailsBeforeInventoryReservationWhenNoProviderSupportsAmount() throws Exception {
        String username = "unsupported-amount-customer@example.com";
        user(username);
        ProductModel product = product("Large Order", 5, "1000000.00", "USD");
        long cartId = createCart(username);
        addItem(username, cartId, product.getProductId(), 1);

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(username, "checkout:write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("No enabled payment provider is available for this order"));

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(auditEventRepository.findAll()).isEmpty();

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt(username, "cart:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    private long createCart(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/cart")
                        .with(customerJwt(username, "cart:write")))
                .andExpect(status().isOk())
                .andReturn();

        Number cartId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartId");
        return cartId.longValue();
    }

    private void assertConcurrentMutationWhileCheckoutLocksCartConflicts(CartMutation mutation) throws Exception {
        String username = "checkout-cart-race-" + System.nanoTime() + "@example.com";
        user(username);
        ProductModel product = product("Concurrent Checkout Product", 5);
        long cartId = createCart(username);
        addItem(username, cartId, product.getProductId(), 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Future<Integer>> checkoutFuture = new AtomicReference<>();
        AtomicReference<Future<Integer>> mutationFuture = new AtomicReference<>();

        try {
            transactionTemplate().executeWithoutResult(status -> {
                lockInventory(product.getProductId());
                checkoutFuture.set(executor.submit(() -> checkoutStatus(username, cartId)));
                waitForCartLock(cartId);
                mutationFuture.set(executor.submit(() -> mutation.perform(username, cartId, product.getProductId())));
            });

            assertThat(checkoutFuture.get().get(5, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(mutationFuture.get().get(5, TimeUnit.SECONDS)).isEqualTo(409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(cartRepo.findWithItemsById(cartId).orElseThrow().getItems()).isEmpty();

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(4);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
    }

    private void waitForCartLock(long cartId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (isCartLockedByAnotherTransaction(cartId)) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("Checkout did not acquire the cart row lock");
    }

    private boolean isCartLockedByAnotherTransaction(long cartId) {
        TransactionTemplate template = transactionTemplate();
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> jdbcTemplate.queryForObject(
                    "SELECT id FROM cart_model WHERE id = ? FOR UPDATE NOWAIT",
                    Long.class,
                    cartId
            ));
            return false;
        } catch (DataAccessException exception) {
            return true;
        }
    }

    private void lockInventory(long productId) {
        jdbcTemplate.queryForObject(
                "SELECT product_id FROM inventory WHERE product_id = ? FOR UPDATE",
                Long.class,
                productId
        );
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for checkout lock", exception);
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void addItem(String username, long cartId, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/cart/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": %d
                                }
                                """.formatted(productId, quantity))
                        .with(customerJwt(username, "cart:write")))
                .andExpect(status().isOk());
    }

    private int addItemStatus(String username, long cartId, long productId) throws Exception {
        return mockMvc.perform(post("/cart/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 1
                                }
                                """.formatted(productId))
                        .with(customerJwt(username, "cart:write")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int updateItemStatus(String username, long cartId, long productId) throws Exception {
        return mockMvc.perform(put("/cart/{cartId}/items/{productId}", cartId, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 2
                                }
                                """)
                        .with(customerJwt(username, "cart:write")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int removeItemStatus(String username, long cartId, long productId) throws Exception {
        return mockMvc.perform(delete("/cart/{cartId}/items/{productId}", cartId, productId)
                        .with(customerJwt(username, "cart:write")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int checkoutStatus(String username, long cartId) throws Exception {
        return mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(username, "checkout:write")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private UserModel user(String username) {
        return userRepo.save(UserModel.builder()
                .identitySubject(username)
                .username(username)
                .email(username)
                .firstName("Checkout")
                .lastName("Customer")
                .build());
    }

    private ProductModel product(String name, int availableQuantity) {
        return product(name, availableQuantity, "10.00", "USD");
    }

    private ProductModel product(String name, int availableQuantity, String price, String currency) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name(name)
                .price(new java.math.BigDecimal(price))
                .currency(currency)
                .stock(availableQuantity)
                .active(true)
                .build());
        inventoryRepository.save(new InventoryRecord(product.getProductId(), availableQuantity, 0));
        return product;
    }

    private RequestPostProcessor customerJwt(String username, String scope) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(username)
                        .claim("preferred_username", username)
                        .claim("email", username))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("SCOPE_" + scope)
                );
    }

    @FunctionalInterface
    private interface CartMutation {

        int perform(String username, long cartId, long productId) throws Exception;
    }
}
