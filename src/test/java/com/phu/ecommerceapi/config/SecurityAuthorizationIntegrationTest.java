package com.phu.ecommerceapi.config;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.Product.ProductRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
    private AuditEventRepository auditEventRepository;

    @AfterEach
    void cleanUpData() {
        auditEventRepository.deleteAll();
        cartRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void sensitiveEndpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/customer/profile/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/cart"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("00000000-0000-0000-0000-000000000001")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/events"))
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
                        .with(jwtWith("admin-subject", role("ADMIN"), scope("audit:read"))))
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
