package com.phu.ecommerceapi.inventory.api;

import com.phu.ecommerceapi.inventory.application.StockEventBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockEventStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockEventBroadcaster stockEventBroadcaster;

    @AfterEach
    void closeEmitters() {
        stockEventBroadcaster.completeAll();
    }

    @Test
    void stockStreamRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/products/{productId}/stock/stream", 10L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutStockStreamScopeCannotOpenStockStream() throws Exception {
        mockMvc.perform(get("/products/{productId}/stock/stream", 10L)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_product:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockStreamScopeCanOpenStockStream() throws Exception {
        mockMvc.perform(get("/products/{productId}/stock/stream", 10L)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_stock:stream"))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)));
    }
}
