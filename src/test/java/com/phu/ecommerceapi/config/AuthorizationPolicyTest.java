package com.phu.ecommerceapi.config;

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

    @Test
    void sensitiveUserReadRequiresAdminOrAuditorRoleAndUserReadScope() throws Exception {
        mockMvc.perform(get("/allUserInfo").with(jwt()
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_user:read")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/allUserInfo").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/allUserInfo").with(jwt()
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_user:read")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(get("/allUserInfo").with(jwt()
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
}
