package com.phu.ecommerceapi.shared.api;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    @Test
    void missingRequestIdProducesInternalUuidResponseHeader() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter("");
        MockHttpServletRequest request = request("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = (String) servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            assertThat(requestId).matches(UUID_PATTERN);
            assertThat(servletRequest.getAttribute(RequestIdFilter.EXTERNAL_CORRELATION_ID_ATTRIBUTE)).isNull();
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
            assertThat(MDC.get(RequestIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo(requestId);
            assertThat(RequestMetadataHolder.current().requestId()).isEqualTo(requestId);
            assertThat(RequestMetadataHolder.current().externalCorrelationId()).isNull();
            assertThat(RequestMetadataHolder.current().userAgent()).isEqualTo("test-agent");
        });

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).matches(UUID_PATTERN);
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNull();
        assertThat(MDC.get(RequestIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(RequestMetadataHolder.current()).isEqualTo(RequestMetadata.unknown());
    }

    @Test
    void validClientRequestIdIsStoredAsExternalCorrelationOnly() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter("");
        MockHttpServletRequest request = request("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client.req-123");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = (String) servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            assertThat(requestId).matches(UUID_PATTERN);
            assertThat(requestId).isNotEqualTo("client.req-123");
            assertThat(servletRequest.getAttribute(RequestIdFilter.EXTERNAL_CORRELATION_ID_ATTRIBUTE))
                    .isEqualTo("client.req-123");
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
            assertThat(MDC.get(RequestIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("client.req-123");
            assertThat(RequestMetadataHolder.current().requestId()).isEqualTo(requestId);
            assertThat(RequestMetadataHolder.current().externalCorrelationId()).isEqualTo("client.req-123");
        });

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).matches(UUID_PATTERN);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotEqualTo("client.req-123");
    }

    @Test
    void invalidOrOversizedClientRequestIdIsIgnored() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter("");
        MockHttpServletResponse invalidCharacterResponse = new MockHttpServletResponse();
        MockHttpServletRequest invalidCharacterRequest = request("127.0.0.1");
        invalidCharacterRequest.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client request id");

        filter.doFilter(invalidCharacterRequest, invalidCharacterResponse, (servletRequest, servletResponse) ->
                assertThat(RequestMetadataHolder.current().externalCorrelationId()).isNull());

        MockHttpServletResponse oversizedResponse = new MockHttpServletResponse();
        MockHttpServletRequest oversizedRequest = request("127.0.0.1");
        oversizedRequest.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "a".repeat(65));

        filter.doFilter(oversizedRequest, oversizedResponse, (servletRequest, servletResponse) ->
                assertThat(RequestMetadataHolder.current().externalCorrelationId()).isNull());

        assertThat(invalidCharacterResponse.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).matches(UUID_PATTERN);
        assertThat(oversizedResponse.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void untrustedRemoteAddressIgnoresForwardedFor() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter("10.0.0.0/8");
        MockHttpServletRequest request = request("198.51.100.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.5");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(RequestMetadataHolder.current().ipAddress()).isEqualTo("198.51.100.20"));
    }

    @Test
    void trustedProxyUsesFirstValidForwardedForIp() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter("10.0.0.0/8");
        MockHttpServletRequest request = request("10.1.2.3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Forwarded-For", "not-an-ip, 203.0.113.10, 198.51.100.20");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(RequestMetadataHolder.current().ipAddress()).isEqualTo("203.0.113.10"));
    }

    @Test
    void trustedProxyFallsBackToRemoteAddressWhenForwardedForHasNoValidIp() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter("10.0.0.0/8");
        MockHttpServletRequest request = request("10.1.2.3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Forwarded-For", "not-an-ip, also-not-an-ip");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(RequestMetadataHolder.current().ipAddress()).isEqualTo("10.1.2.3"));
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "test-agent");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
