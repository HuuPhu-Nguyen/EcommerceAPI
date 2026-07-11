package com.phu.ecommerceapi.documentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentsBankCriticalEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Banking-Grade E-Commerce API"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"))
                .andExpect(jsonPath("$.paths['/checkout'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/payments'].post.parameters[?(@.name == 'Idempotency-Key')]").exists())
                .andExpect(jsonPath("$.paths['/payments/{paymentId}/refunds'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/payments/provider-webhooks/stripe'].post.tags[0]")
                        .value("Provider Webhooks"))
                .andExpect(jsonPath("$.paths['/payments/provider-webhooks/stripe'].post.summary")
                        .value("Handle Stripe provider webhook"))
                .andExpect(jsonPath("$.paths['/payments/provider-webhooks/stripe'].post.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.paths['/ledger/transactions'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/audit/events'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/audit/events/verification'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/reconciliation/report'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/products/{productId}/stock/stream'].get.responses['200'].content['text/event-stream']").exists());
    }
}
