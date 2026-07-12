package com.phu.ecommerceapi.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    void rejectsWebhookPayloadWhenContentLengthExceedsLimitBeforeController() throws Exception {
        String path = "/payments/provider-webhooks/fake";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.11");
        request.setRequestURI(path);
        request.setContent(body(65));
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean controllerReached = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> controllerReached.set(true));

        assertPayloadTooLarge(response, path);
        assertThat(controllerReached).isFalse();
    }

    @Test
    void rejectsWebhookPayloadWithoutContentLengthWhenActualBodyExceedsLimit() throws Exception {
        String path = "/payments/provider-webhooks/fake";
        MockHttpServletRequest request = requestWithContentLength("POST", path, -1, body(65));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean controllerReached = new AtomicBoolean(false);

        newFilter().doFilter(request, response, (servletRequest, servletResponse) -> controllerReached.set(true));

        assertPayloadTooLarge(response, path);
        assertThat(controllerReached).isFalse();
    }

    @Test
    void cachesWebhookPayloadWithoutContentLengthWhenActualBodyIsExactlyLimit() throws Exception {
        String path = "/payments/provider-webhooks/stripe";
        String requestBody = "a".repeat(64);
        MockHttpServletRequest request = requestWithContentLength(
                "POST",
                path,
                -1,
                requestBody.getBytes(StandardCharsets.UTF_8)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> inputStreamBody = new AtomicReference<>();
        AtomicReference<String> readerBody = new AtomicReference<>();
        AtomicReference<Boolean> cachedRequest = new AtomicReference<>(false);

        newFilter().doFilter(request, response, (servletRequest, servletResponse) -> {
            cachedRequest.set(servletRequest instanceof CachedBodyHttpServletRequest);
            inputStreamBody.set(servletRequest.getInputStream().readAllBytes());
            readerBody.set(servletRequest.getReader().lines().collect(Collectors.joining("\n")));
        });

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(cachedRequest).hasValue(true);
        assertThat(inputStreamBody.get()).isEqualTo(requestBody.getBytes(StandardCharsets.UTF_8));
        assertThat(readerBody).hasValue(requestBody);
    }

    @Test
    void rejectsWebhookPayloadWhenContentLengthIsLowerThanActualBody() throws Exception {
        String path = "/payments/provider-webhooks/stripe";
        MockHttpServletRequest request = requestWithContentLength("POST", path, 1, body(65));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean controllerReached = new AtomicBoolean(false);

        newFilter().doFilter(request, response, (servletRequest, servletResponse) -> controllerReached.set(true));

        assertPayloadTooLarge(response, path);
        assertThat(controllerReached).isFalse();
    }

    @Test
    void doesNotCacheNonWebhookRequestBodies() throws Exception {
        String path = "/orders";
        byte[] requestBody = "regular body".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = requestWithContentLength("POST", path, -1, requestBody);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> cachedRequest = new AtomicReference<>(true);
        AtomicReference<byte[]> inputStreamBody = new AtomicReference<>();

        newFilter().doFilter(request, response, (servletRequest, servletResponse) -> {
            cachedRequest.set(servletRequest instanceof CachedBodyHttpServletRequest);
            inputStreamBody.set(servletRequest.getInputStream().readAllBytes());
        });

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(cachedRequest).hasValue(false);
        assertThat(inputStreamBody.get()).isEqualTo(requestBody);
    }

    @Test
    void rateLimitsProfileReadsAndProvisioningByClientAddress() throws Exception {
        assertAllowed("GET", "/customer/profile/me");
        assertRateLimited(perform("GET", "/customer/profile/me"), "/customer/profile/me");

        AbuseRateLimitFilter provisioningFilter = newFilter();
        assertAllowed(provisioningFilter, "POST", "/customer/profile/me");
        assertRateLimited(
                perform(provisioningFilter, "POST", "/customer/profile/me"),
                "/customer/profile/me"
        );
    }

    @Test
    void doesNotSpecialCaseRemovedRegistrationEndpoint() throws Exception {
        assertAllowed("POST", "/register");
        assertAllowed("POST", "/register");
    }

    @Test
    void doesNotRateLimitUnlistedEndpoint() throws Exception {
        assertAllowed("GET", "/products");
        assertAllowed("GET", "/products");
    }

    private void assertAllowed(String method, String path) throws Exception {
        assertAllowed(filter, method, path);
    }

    private void assertAllowed(AbuseRateLimitFilter targetFilter, String method, String path) throws Exception {
        MockHttpServletResponse response = perform(targetFilter, method, path);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    private MockHttpServletResponse perform(String method, String path) throws Exception {
        return perform(filter, method, path);
    }

    private MockHttpServletResponse perform(AbuseRateLimitFilter targetFilter, String method, String path)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.10");
        request.setRequestURI(path);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        targetFilter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void assertRateLimited(MockHttpServletResponse response, String path) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString())
                .contains("RATE_LIMITED", path, "request-123")
                .doesNotContain("Authorization", "Idempotency-Key");
    }

    private void assertPayloadTooLarge(MockHttpServletResponse response, String path) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(response.getContentAsString())
                .contains("VALIDATION_FAILED", path, "request-123")
                .doesNotContain("Authorization", "Idempotency-Key");
    }

    private AbuseRateLimitFilter newFilter() {
        return new AbuseRateLimitFilter(
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC),
                true,
                60,
                1,
                1,
                1,
                64
        );
    }

    private byte[] body(int size) {
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) 'a');
        return body;
    }

    private MockHttpServletRequest requestWithContentLength(
            String method,
            String path,
            long contentLength,
            byte[] body
    ) {
        MockHttpServletRequest request = new ContentLengthOverrideRequest(method, path, contentLength);
        request.setRemoteAddr("203.0.113.12");
        request.setRequestURI(path);
        request.setContent(body);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        return request;
    }

    private static final class ContentLengthOverrideRequest extends MockHttpServletRequest {

        private final long contentLength;

        private ContentLengthOverrideRequest(String method, String path, long contentLength) {
            super(method, path);
            this.contentLength = contentLength;
        }

        @Override
        public int getContentLength() {
            if (contentLength < 0 || contentLength > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) contentLength;
        }

        @Override
        public long getContentLengthLong() {
            return contentLength;
        }
    }
}
