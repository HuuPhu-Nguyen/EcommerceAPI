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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

        mockMvc.perform(get("/admin/customer-profiles").with(jwt()
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("SCOPE_user:read")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value(USERNAME))
                .andExpect(jsonPath("$[0].identitySubject").value("identity-subject-1"))
                .andExpect(jsonPath("$[0].email").value(USERNAME))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].address").doesNotExist())
                .andExpect(jsonPath("$[0].carts").doesNotExist());
    }

    @Test
    void registrationReturnsSafeProfileDto() throws Exception {
        String requestBody = """
                {
                  "username": "new-customer@example.com",
                  "password": "plain-text-password",
                  "firstName": "New",
                  "lastName": "Customer",
                  "email": "new-customer@example.com",
                  "phone": "555-0100",
                  "address": "Sensitive Address"
                }
                """;

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-customer@example.com"))
                .andExpect(jsonPath("$.email").value("new-customer@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.carts").doesNotExist());
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
                .password("encoded-password")
                .firstName("Profile")
                .lastName("Customer")
                .email(USERNAME)
                .phone("555-0100")
                .address("Sensitive Address")
                .build();
        userRepo.save(user);
    }
}
