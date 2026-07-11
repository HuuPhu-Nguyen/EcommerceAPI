package com.phu.ecommerceapi.payment.api;

import com.phu.ecommerceapi.payment.application.StripeProviderReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.payment-provider.active=stripe",
        "app.payment-provider.enabled=fake,stripe",
        "app.stripe.secret-key=sk_test_safe_placeholder",
        "app.stripe.webhook-secret=whsec_test_safe_placeholder",
        "management.endpoint.health.show-details=always"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderReadinessHealthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripeProviderReadPort stripeProviderReadPort;

    @Test
    void readinessIncludesProviderDetailsWithoutCallingStripeByDefault() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.paymentProviders.status").value("UP"));

        verifyNoInteractions(stripeProviderReadPort);
    }
}
