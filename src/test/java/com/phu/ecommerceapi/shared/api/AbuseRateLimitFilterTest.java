package com.phu.ecommerceapi.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseRateLimitFilterTest {

    private final AbuseRateLimitFilter filter = new AbuseRateLimitFilter(
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC),
            true,
            60,
            1,
            1,
            1,
            1,
            64
    );

    @Test
    void rateLimitsPaymentCreationByClientAddress() throws Exception {
        assertAllowed("POST", "/payments");

        MockHttpServletResponse response = perform("POST", "/payments");

        assertRateLimited(response, "/payments");
    }

    @Test
    void rateLimitsRefundCreationByClientAddress() throws Exception {
        String path = "/payments/638dc8e7-e7c5-47bf-a62f-a5728f9c19be/refunds";
        assertAllowed("POST", path);

        MockHttpServletResponse response = perform("POST", path);

        assertRateLimited(response, path);
    }

    @Test
    void rateLimitsProviderWebhooksByClientAddress() throws Exception {
        String path = "/payments/provider-webhooks/stripe";
        assertAllowed("POST", path);

        MockHttpServletResponse response = perform("POST", path);

        assertRateLimited(response, path);
    }

    @Test
    void rejectsOversizedWebhookPayloadBeforeController() throws Exception {
        String path = "/payments/provider-webhooks/fake";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.11");
        request.setRequestURI(path);
        request.setContent(new byte[65]);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(response.getContentAsString())
                .contains("VALIDATION_FAILED", path, "request-123")
                .doesNotContain("Authorization", "Idempotency-Key");
    }

    @Test
    void rateLimitsRegistrationAndProfileReadsByClientAddress() throws Exception {
        assertAllowed("POST", "/register");
        assertRateLimited(perform("POST", "/register"), "/register");

        assertAllowed("GET", "/customer/profile/me");
        assertRateLimited(perform("GET", "/customer/profile/me"), "/customer/profile/me");
    }

    @Test
    void doesNotRateLimitUnlistedEndpoint() throws Exception {
        assertAllowed("GET", "/products");
        assertAllowed("GET", "/products");
    }

    private void assertAllowed(String method, String path) throws Exception {
        MockHttpServletResponse response = perform(method, path);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    private MockHttpServletResponse perform(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.10");
        request.setRequestURI(path);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void assertRateLimited(MockHttpServletResponse response, String path) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString())
                .contains("RATE_LIMITED", path, "request-123")
                .doesNotContain("Authorization", "Idempotency-Key");
    }
}
