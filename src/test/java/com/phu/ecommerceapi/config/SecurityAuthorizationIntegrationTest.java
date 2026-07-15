package com.phu.ecommerceapi.config;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.application.StockEventBroadcaster;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.Product.ProductRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static com.phu.ecommerceapi.audit.AuditEventTestCleaner.clearAuditEvents;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StockEventBroadcaster stockEventBroadcaster;

    @AfterEach
    void cleanUpData() {
        stockEventBroadcaster.completeAll();
        clearAuditEvents(jdbcTemplate);
        cartRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void sensitiveEndpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/customer/profile/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/customer/profile/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/cart"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("00000000-0000-0000-0000-000000000001")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ledger/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/products/{productId}/stock/stream", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerEndpointsRequireCustomerRoleAndExpectedScope() throws Exception {
        mockMvc.perform(get("/customer/profile/me")
                        .with(jwtWith("profile-subject", role("CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/customer/profile/me")
                        .with(jwtWith("profile-subject", scope("profile:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/customer/profile/me")
                        .with(jwtWith("profile-subject", role("CUSTOMER"), scope("profile:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/customer/profile/me")
                        .with(jwtWith("profile-write-subject", role("CUSTOMER"), scope("profile:write"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/cart")
                        .with(jwtWith("cart-subject", role("CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/cart")
                        .with(jwtWith("cart-subject", scope("cart:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "security-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("00000000-0000-0000-0000-000000000001"))
                        .with(jwtWith("payment-subject", role("CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "security-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("00000000-0000-0000-0000-000000000001"))
                        .with(jwtWith("payment-subject", scope("payment:create"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminProductWriteRequiresAdminRoleAndProductWriteScope() throws Exception {
        String requestBody = productJson("security-admin-product");

        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwtWith("customer-subject", role("CUSTOMER"), scope("product:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwtWith("admin-subject", role("ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwtWith("admin-subject", role("ADMIN"), scope("product:write"))))
                .andExpect(status().isOk());
    }

    @Test
    void auditorEndpointsRequireAdminOrAuditorRoleAndAuditReadScope() throws Exception {
        mockMvc.perform(get("/audit/events")
                        .with(jwtWith("customer-subject", role("CUSTOMER"), scope("audit:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/events")
                        .with(jwtWith("auditor-subject", role("AUDITOR"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/events")
                        .with(jwtWith("auditor-subject", role("AUDITOR"), scope("audit:read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reconciliation/report")
                        .with(jwtWith("auditor-subject", role("AUDITOR"), scope("audit:read"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/reconciliation/runs")
                        .with(jwtWith("auditor-subject", role("AUDITOR"), scope("audit:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reconciliation/runs")
                        .with(jwtWith("admin-subject", role("ADMIN"), scope("audit:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reconciliation/runs")
                        .with(jwtWith("admin-subject", role("ADMIN"), scope("reconciliation:run"))))
                .andExpect(status().isOk());
    }

    @Test
    void productReadEndpointsRequireProductReadScope() throws Exception {
        mockMvc.perform(get("/products")
                        .with(jwtWith("product-subject", role("CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/products")
                        .with(jwtWith("product-subject", scope("product:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/products")
                        .with(jwtWith("product-subject", scope("product:read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products/{id}", -1)
                        .with(jwtWith("product-subject", scope("product:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockStreamRequiresStockStreamScope() throws Exception {
        mockMvc.perform(get("/products/{productId}/stock/stream", 1L)
                        .with(jwtWith("stock-subject", scope("product:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/products/{productId}/stock/stream", 1L)
                        .with(jwtWith("stock-subject", scope("stock:stream"))))
                .andExpect(status().isOk());
    }

    @Test
    void ledgerReadRequiresAdminOrAuditorRoleAndLedgerReadScope() throws Exception {
        mockMvc.perform(get("/ledger/transactions")
                        .with(jwtWith("customer-subject", role("CUSTOMER"), scope("ledger:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/ledger/transactions")
                        .with(jwtWith("auditor-subject", role("AUDITOR"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/ledger/transactions")
                        .with(jwtWith("auditor-subject", role("AUDITOR"), scope("ledger:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void cartOwnershipUsesDurableSubjectInsteadOfUsernameOrEmailClaims() throws Exception {
        UserModel owner = userRepo.save(user("owner-subject", "shared-security@example.com"));
        CartModel cart = cartRepo.save(new CartModel(owner));

        mockMvc.perform(get("/cart/{cartId}", cart.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject("attacker-subject")
                                        .claim("preferred_username", owner.getUsername())
                                        .claim("email", owner.getEmail()))
                                .authorities(role("CUSTOMER"), scope("cart:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/cart/{cartId}", cart.getId())
                        .with(jwtWith(owner.getIdentitySubject(), role("CUSTOMER"), scope("cart:read"))))
                .andExpect(status().isOk());
    }

    private UserModel user(String subject, String email) {
        return UserModel.builder()
                .identitySubject(subject)
                .username(email)
                .email(email)
                .firstName("Security")
                .lastName("Customer")
                .build();
    }

    private RequestPostProcessor jwtWith(String subject, SimpleGrantedAuthority... authorities) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@example.com"))
                .authorities(authorities);
    }

    private SimpleGrantedAuthority role(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }

    private SimpleGrantedAuthority scope(String scope) {
        return new SimpleGrantedAuthority("SCOPE_" + scope);
    }

    private String productJson(String name) {
        return """
                {
                  "name": "%s",
                  "price": 15.50,
                  "stock": 5,
                  "currency": "USD"
                }
                """.formatted(name);
    }

    private String paymentJson(String orderId) {
        return """
                {
                  "orderId": "%s",
                  "paymentMethodToken": "pm_approved"
                }
                """.formatted(orderId);
    }
}
