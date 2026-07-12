package com.phu.ecommerceapi.customer.api;

import com.phu.ecommerceapi.config.SecurityConfig;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfilePage;
import com.phu.ecommerceapi.customer.application.CustomerProfileService;
import com.phu.ecommerceapi.identity.application.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerProfileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused-jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/test"
})
class CustomerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerProfileService customerProfileService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void adminProfileListingReturnsPagedResponse() throws Exception {
        CustomerProfilePage page = new CustomerProfilePage(
                List.of(new CustomerProfile(
                        1L,
                        "identity-subject-1",
                        "profile-customer@example.com",
                        "Profile",
                        "Customer",
                        "profile-customer@example.com"
                )),
                0,
                50,
                1,
                1
        );
        when(customerProfileService.getProfiles(0, 50)).thenReturn(page);

        mockMvc.perform(get("/admin/customer-profiles").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("profile-customer@example.com"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(customerProfileService).getProfiles(0, 50);
    }

    @Test
    void adminProfileListingUsesRequestedPageAndSize() throws Exception {
        CustomerProfilePage page = new CustomerProfilePage(List.of(), 1, 2, 3, 2);
        when(customerProfileService.getProfiles(1, 2)).thenReturn(page);

        mockMvc.perform(get("/admin/customer-profiles")
                        .param("page", "1")
                        .param("size", "2")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(customerProfileService).getProfiles(1, 2);
    }

    @Test
    void adminProfileListingRejectsOversizedPageSizeBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/admin/customer-profiles")
                        .param("size", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));

        verifyNoInteractions(customerProfileService);
    }

    @Test
    void adminProfileListingRejectsLowerBoundViolationsBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/admin/customer-profiles")
                        .param("page", "-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("page must be greater than or equal to 0"));

        mockMvc.perform(get("/admin/customer-profiles")
                        .param("size", "0")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));

        verifyNoInteractions(customerProfileService);
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject("admin-subject"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_user:read")
                );
    }
}
