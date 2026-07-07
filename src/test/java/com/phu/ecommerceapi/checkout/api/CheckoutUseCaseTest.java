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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        InventoryRecord availableInventory = inventoryRepository.findById(availableProduct.getProductId()).orElseThrow();
        InventoryRecord contestedInventory = inventoryRepository.findById(contestedProduct.getProductId()).orElseThrow();
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

    private long createCart(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/cart")
                        .with(customerJwt(username, "cart:write")))
                .andExpect(status().isOk())
                .andReturn();

        Number cartId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartId");
        return cartId.longValue();
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
        ProductModel product = productRepo.save(ProductModel.builder()
                .name(name)
                .price(new java.math.BigDecimal("10.00"))
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
}
