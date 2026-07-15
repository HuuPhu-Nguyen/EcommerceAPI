package com.phu.ecommerceapi.config;

import com.phu.ecommerceapi.inventory.application.StockEventBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockEventBroadcaster stockEventBroadcaster;

    @AfterEach
    void closeEmitters() {
        stockEventBroadcaster.completeAll();
    }

    @Test
    void sensitiveUserReadRequiresAdminOrAuditorRoleAndUserReadScope() throws Exception {
        mockMvc.perform(get("/admin/customer-profiles").with(jwt()
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_user:read")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/customer-profiles").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/customer-profiles").with(jwt()
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_user:read")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/customer-profiles").with(jwt()
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_AUDITOR"),
                                new SimpleGrantedAuthority("SCOPE_user:read")
                        )))
                .andExpect(status().isOk());
    }

    @Test
    void productWriteRequiresAdminRoleAndProductWriteScope() throws Exception {
        String requestBody = """
                {
                  "name": "Test Product",
                  "price": 15.50,
                  "stock": 5
                }
                """;

        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_product:write")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_product:write")
                        )))
                .andExpect(status().isOk());
    }

    @Test
    void profileWriteRequiresCustomerRoleAndProfileWriteScope() throws Exception {
        mockMvc.perform(post("/customer/profile/me")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_profile:read")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/customer/profile/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_profile:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/customer/profile/me")
                        .with(jwt().jwt(jwt -> jwt.subject("policy-profile-write-subject"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                        new SimpleGrantedAuthority("SCOPE_profile:write")
                                )))
                .andExpect(status().isOk());
    }

    @Test
    void reconciliationRunRequiresAdminRoleAndRunScope() throws Exception {
        mockMvc.perform(post("/reconciliation/runs")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_AUDITOR"),
                                new SimpleGrantedAuthority("SCOPE_audit:read")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reconciliation/runs")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_audit:read")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reconciliation/runs")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_reconciliation:run")
                        )))
                .andExpect(status().isOk());
    }

    @Test
    void productReadRequiresProductReadScope() throws Exception {
        mockMvc.perform(get("/products").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/products").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products/{id}", -1).with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockStreamRequiresStockStreamScope() throws Exception {
        mockMvc.perform(get("/products/{productId}/stock/stream", 1L).with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/products/{productId}/stock/stream", 1L).with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:stream"))))
                .andExpect(status().isOk());
    }
}
