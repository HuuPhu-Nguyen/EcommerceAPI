package com.phu.ecommerceapi.cart.api;

import com.jayway.jsonpath.JsonPath;
import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.catalog.infrastructure.ProductRepo;
import com.phu.ecommerceapi.customer.infrastructure.UserModel;
import com.phu.ecommerceapi.customer.infrastructure.UserRepo;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartUseCaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void resetData() {
        orderRepository.deleteAll();
        cartRepo.deleteAll();
        userRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @AfterEach
    void cleanUpData() {
        resetData();
    }

    @Test
    void customerCanCreateViewAddUpdateAndRemoveCartItems() throws Exception {
        user("customer@example.com");
        ProductModel product = product("Mechanical Keyboard", 10);

        long cartId = createCart("customer@example.com");

        mockMvc.perform(post("/cart/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 2
                                }
                                """.formatted(product.getProductId()))
                        .with(customerJwt("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cartId))
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.items[0].productId").value(product.getProductId()))
                .andExpect(jsonPath("$.items[0].productName").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].currency").value("USD"))
                .andExpect(jsonPath("$.items[0].cart").doesNotExist())
                .andExpect(jsonPath("$.items[0].productModel").doesNotExist());

        mockMvc.perform(put("/cart/{cartId}/items/{productId}", cartId, product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 3
                                }
                                """)
                        .with(customerJwt("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(30.00))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt("customer@example.com", "cart:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cartId))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/cart/{cartId}/items/{productId}", cartId, product.getProductId())
                        .with(customerJwt("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.00))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void crossCustomerCartReadIsDenied() throws Exception {
        user("owner@example.com");
        long cartId = createCart("owner@example.com");

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt("attacker@example.com", "cart:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void addingMoreThanAvailableInventoryReturnsOutOfStockAndLeavesCartEmpty() throws Exception {
        user("customer@example.com");
        ProductModel product = product("Limited Headphones", 1);
        long cartId = createCart("customer@example.com");

        mockMvc.perform(post("/cart/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 2
                                }
                                """.formatted(product.getProductId()))
                        .with(customerJwt("customer@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

        mockMvc.perform(get("/cart/{cartId}", cartId)
                        .with(customerJwt("customer@example.com", "cart:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0.00));
    }

    @Test
    void productDeactivationDoesNotDeleteExistingCartItems() throws Exception {
        user("customer@example.com");
        ProductModel product = product("Deactivated Later", 5);
        long cartId = createCart("customer@example.com");

        mockMvc.perform(post("/cart/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 1
                                }
                                """.formatted(product.getProductId()))
                        .with(customerJwt("customer@example.com")))
                .andExpect(status().isOk());

        ProductModel savedProduct = productRepo.findById(product.getProductId()).orElseThrow();
        savedProduct.setActive(false);
        productRepo.saveAndFlush(savedProduct);

        assertThat(cartRepo.findWithItemsById(cartId).orElseThrow().getItems())
                .hasSize(1);
    }

    private long createCart(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/cart")
                        .with(customerJwt(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").exists())
                .andExpect(jsonPath("$.total").value(0.00))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andReturn();

        Number cartId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartId");
        return cartId.longValue();
    }

    private UserModel user(String username) {
        return userRepo.save(UserModel.builder()
                .identitySubject(username)
                .username(username)
                .email(username)
                .firstName("Test")
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

    private RequestPostProcessor customerJwt(String username) {
        return customerJwt(username, "cart:write");
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
