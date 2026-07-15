package com.phu.ecommerceapi.customer.api;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
class CustomerProfileBoundaryTest {

    private static final String USERNAME = "profile-customer@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @BeforeEach
    void resetUsers() {
        orderRepository.deleteAll();
        cartRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void currentProfileReturnsSafeDtoForAuthenticatedCustomer() throws Exception {
        saveCustomer();

        mockMvc.perform(get("/customer/profile/me").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("identity-subject-1")
                                .claim("preferred_username", USERNAME)
                                .claim("email", USERNAME))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_profile:read")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identitySubject").value("identity-subject-1"))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value(USERNAME))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.carts").doesNotExist());
    }

    @Test
    void adminProfileListingReturnsSafeDtos() throws Exception {
        saveCustomer();

        mockMvc.perform(get("/admin/customer-profiles").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value(USERNAME))
                .andExpect(jsonPath("$.items[0].identitySubject").value("identity-subject-1"))
                .andExpect(jsonPath("$.items[0].email").value(USERNAME))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.items[0].address").doesNotExist())
                .andExpect(jsonPath("$.items[0].carts").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void adminProfileListingDefaultsToAtMostFiftyProfiles() throws Exception {
        saveCustomers(55);

        mockMvc.perform(get("/admin/customer-profiles").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(55))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void adminProfileListingUsesRequestedSizeAndMetadata() throws Exception {
        saveCustomers(3);

        mockMvc.perform(get("/admin/customer-profiles")
                        .param("size", "2")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].username").value("profile-customer-001@example.com"))
                .andExpect(jsonPath("$.items[1].username").value("profile-customer-002@example.com"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void adminProfileListingRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/admin/customer-profiles")
                        .param("size", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
    }

    @Test
    void profileProvisioningRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/customer/profile/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileProvisioningRejectsReadOnlyProfileScope() throws Exception {
        mockMvc.perform(post("/customer/profile/me")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("read-only-profile-subject"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                        new SimpleGrantedAuthority("SCOPE_profile:read")
                                )))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileProvisioningCreatesSafeProfileFromAuthenticatedSubject() throws Exception {
        mockMvc.perform(post("/customer/profile/me")
                        .with(customerJwt("provision-subject-1", "new-customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-customer@example.com"))
                .andExpect(jsonPath("$.identitySubject").value("provision-subject-1"))
                .andExpect(jsonPath("$.email").value("new-customer@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.carts").doesNotExist());

        UserModel user = userRepo.findByIdentitySubject("provision-subject-1");
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("new-customer@example.com");
        assertThat(user.getEmail()).isEqualTo("new-customer@example.com");
        assertThat(user.getFirstName()).isNull();
        assertThat(user.getLastName()).isNull();
    }

    @Test
    void profileProvisioningReturnsExistingProfileForSameSubject() throws Exception {
        mockMvc.perform(post("/customer/profile/me")
                        .with(customerJwt("provision-subject-2", "first-profile@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("first-profile@example.com"));

        mockMvc.perform(post("/customer/profile/me")
                        .with(customerJwt("provision-subject-2", "changed-profile@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("first-profile@example.com"))
                .andExpect(jsonPath("$.email").value("first-profile@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepo.findAll()).hasSize(1);
    }

    @Test
    void currentProfileWorksAfterProvisioning() throws Exception {
        RequestPostProcessor jwt = customerJwt("provision-subject-3", "profile-after-provision@example.com");

        mockMvc.perform(post("/customer/profile/me").with(jwt))
                .andExpect(status().isOk());

        mockMvc.perform(get("/customer/profile/me").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identitySubject").value("provision-subject-3"))
                .andExpect(jsonPath("$.username").value("profile-after-provision@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void currentProfileDoesNotFallBackToMatchingEmail() throws Exception {
        saveCustomer();

        mockMvc.perform(get("/customer/profile/me").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("different-subject")
                                .claim("preferred_username", USERNAME)
                                .claim("email", USERNAME))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_profile:read")
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void saveCustomer() {
        UserModel user = UserModel.builder()
                .username(USERNAME)
                .identitySubject("identity-subject-1")
                .firstName("Profile")
                .lastName("Customer")
                .email(USERNAME)
                .phone("555-0100")
                .address("Sensitive Address")
                .build();
        userRepo.save(user);
    }

    private void saveCustomers(int count) {
        for (int index = 1; index <= count; index++) {
            userRepo.save(UserModel.builder()
                    .username("profile-customer-%03d@example.com".formatted(index))
                    .identitySubject("identity-subject-%03d".formatted(index))
                    .firstName("Profile")
                    .lastName("Customer")
                    .email("profile-customer-%03d@example.com".formatted(index))
                    .phone("555-0100")
                    .address("Sensitive Address")
                    .build());
        }
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_user:read")
                );
    }

    private RequestPostProcessor customerJwt(String subject, String username) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(subject)
                        .claim("preferred_username", username)
                        .claim("email", username))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("SCOPE_profile:read"),
                        new SimpleGrantedAuthority("SCOPE_profile:write")
                );
    }
}
