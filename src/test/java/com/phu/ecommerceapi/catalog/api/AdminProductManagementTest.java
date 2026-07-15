package com.phu.ecommerceapi.catalog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.phu.ecommerceapi.audit.AuditEventTestCleaner.clearAuditEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetData() {
        outboxEventRepository.deleteAll();
        clearAuditEvents(jdbcTemplate);
        orderRepository.deleteAll();
        cartRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Denied Product", "12.00", 5, true))
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_product:write")
                        )))
                .andExpect(status().isForbidden());

        assertThat(productRepo.findAll()).isEmpty();
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    @Test
    void adminCanCreateProductAndAuditEventIsRecorded() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Admin Product", "19.99", 8, true))
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Product"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(productRepo.findAll()).hasSize(1);
        assertThat(auditEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getActorSubject()).isEqualTo("admin-subject");
                    assertThat(event.getAction()).isEqualTo("PRODUCT_CREATED");
                    assertThat(event.getResourceType()).isEqualTo("PRODUCT");
                });
    }

    @Test
    void overlongProductNameIsRejectedBeforePersistence() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("p".repeat(121), "19.99", 8, true))
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(productRepo.findAll()).isEmpty();
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    @Test
    void adminCanUpdateAndDeactivateProductWithAuditEvents() throws Exception {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name("Original Product")
                .price(new java.math.BigDecimal("10.00"))
                .stock(3)
                .active(true)
                .build());

        mockMvc.perform(put("/admin/products/{productId}", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Updated Product", "25.00", 11, true))
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.stock").value(11))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/admin/products/{productId}/deactivate", product.getProductId())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        ProductModel savedProduct = productRepo.findById(product.getProductId()).orElseThrow();
        assertThat(savedProduct.getName()).isEqualTo("Updated Product");
        assertThat(savedProduct.getStock()).isEqualTo(11);
        assertThat(savedProduct.isActive()).isFalse();
        assertThat(inventoryRepository.findById(product.getProductId()))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableQuantity()).isEqualTo(11);
                    assertThat(inventory.getReservedQuantity()).isZero();
                });
        assertThat(auditEventRepository.findAll())
                .extracting("action")
                .containsExactly("PRODUCT_UPDATED", "PRODUCT_DEACTIVATED");
    }

    @Test
    void adminStockUpdatePreservesReservedQuantityAndPublishesFinalAvailableStock() throws Exception {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name("Reserved Product")
                .price(new java.math.BigDecimal("10.00"))
                .stock(4)
                .active(true)
                .build());
        inventoryRepository.save(new InventoryRecord(product.getProductId(), 4, 2));
        outboxEventRepository.deleteAll();

        mockMvc.perform(put("/admin/products/{productId}", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Reserved Product Updated", "15.00", 9, true))
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(9));

        InventoryRecord inventory = inventoryRepository.findById(product.getProductId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(9);
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(productRepo.findById(product.getProductId()).orElseThrow().getStock()).isEqualTo(9);

        mockMvc.perform(get("/products/{id}", product.getProductId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(inventory.getAvailableQuantity()));

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("PRODUCT");
                    assertThat(event.getAggregateId()).isEqualTo(Long.toString(product.getProductId()));
                    assertThat(event.getEventType()).isEqualTo("StockChanged");
                    JsonNode payload = readPayload(event.getPayload());
                    assertThat(payload.get("availableQuantity").asInt()).isEqualTo(9);
                    assertThat(payload.get("reservedQuantity").asInt()).isEqualTo(2);
                    assertThat(payload.get("reason").asText()).isEqualTo("AVAILABLE_QUANTITY_SET");
                });
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject("admin-subject"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_product:write")
                );
    }

    private String productJson(String name, String price, int stock, boolean active) {
        return """
                {
                  "name": "%s",
                  "price": %s,
                  "stock": %d,
                  "active": %s
                }
                """.formatted(name, price, stock, active);
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new AssertionError("Outbox payload is not valid JSON", exception);
        }
    }
}
