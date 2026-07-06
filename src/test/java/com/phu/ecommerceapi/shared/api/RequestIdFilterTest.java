package com.phu.ecommerceapi.shared.api;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void requestIdIsReturnedStoredInMetadataAndAddedToLogContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-123");
        request.addHeader("User-Agent", "test-agent");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("req-123");
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("req-123");
            assertThat(MDC.get(RequestIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("req-123");
            assertThat(RequestMetadataHolder.current().requestId()).isEqualTo("req-123");
            assertThat(RequestMetadataHolder.current().userAgent()).isEqualTo("test-agent");
        });

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-123");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNull();
        assertThat(MDC.get(RequestIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(RequestMetadataHolder.current()).isEqualTo(RequestMetadata.unknown());
    }
}
