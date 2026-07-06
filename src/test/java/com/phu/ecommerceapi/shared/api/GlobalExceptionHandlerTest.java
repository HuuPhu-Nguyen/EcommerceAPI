package com.phu.ecommerceapi.shared.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionResponseIncludesProblemDetailsAndRequestId() {
        MockHttpServletRequest request = requestWithId("/products/99", "req-123");

        ResponseEntity<ProblemDetail> response = handler.handleApiException(
                new NotFoundException("Product not found"),
                request
        );

        ProblemDetail body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.getDetail()).isEqualTo("Product not found");
        assertThat(body.getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(body.getProperties()).containsEntry("path", "/products/99");
        assertThat(body.getProperties()).containsEntry("requestId", "req-123");
    }

    @Test
    void outOfStockUsesConflictStatusWithSpecificErrorCode() {
        MockHttpServletRequest request = requestWithId("/cart/addItem", "req-456");

        ResponseEntity<ProblemDetail> response = handler.handleApiException(
                new OutOfStockException("Not enough stock"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "OUT_OF_STOCK");
    }

    @Test
    void accessDeniedMapsToForbidden() {
        MockHttpServletRequest request = requestWithId("/admin/products", "req-789");

        ResponseEntity<ProblemDetail> response = handler.handleAccessDenied(
                new AccessDeniedException("denied"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties()).containsEntry("code", "FORBIDDEN");
    }

    private MockHttpServletRequest requestWithId(String path, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
