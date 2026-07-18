package com.phu.ecommerceapi.shared.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
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
        MockHttpServletRequest request = requestWithId("/cart/1/items", "req-456");

        ResponseEntity<ProblemDetail> response = handler.handleApiException(
                new OutOfStockException("Not enough stock"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "OUT_OF_STOCK");
    }

    @Test
    void serviceUnavailableUsesSpecificErrorCode() {
        MockHttpServletRequest request = requestWithId("/payments/99/refunds", "req-503");

        ResponseEntity<ProblemDetail> response = handler.handleApiException(
                new ServiceUnavailableException("Payment provider is unavailable for refund: stripe"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail())
                .isEqualTo("Payment provider is unavailable for refund: stripe");
        assertThat(response.getBody().getProperties()).containsEntry("code", "SERVICE_UNAVAILABLE");
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

    @Test
    void dataIntegrityViolationMapsToSanitizedConflict() {
        MockHttpServletRequest request = requestWithId("/payments", "req-db-conflict");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint ux_payment_order_active; SQL [insert into payment_record]"
        );

        ResponseEntity<ProblemDetail> response = handler.handleDatabaseConflict(exception, request);

        ProblemDetail body = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.getDetail()).isEqualTo("Request conflicts with current resource state");
        assertThat(body.getProperties()).containsEntry("code", "CONFLICT");
        assertThat(body.getProperties()).containsEntry("path", "/payments");
        assertThat(body.getProperties()).doesNotContainKeys("trace", "exception");
        assertThat(body.getDetail()).doesNotContain("SQL", "ux_payment_order_active", "payment_record");
    }

    @Test
    void optimisticLockMapsToSanitizedConflict() {
        MockHttpServletRequest request = requestWithId("/cart/10/items", "req-optimistic");

        ResponseEntity<ProblemDetail> response = handler.handleDatabaseConflict(
                new OptimisticLockException("stale row version"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "CONFLICT");
        assertThat(response.getBody().getDetail()).doesNotContain("stale row version");
    }

    @Test
    void lockAcquisitionFailureMapsToServiceUnavailable() {
        MockHttpServletRequest request = requestWithId("/checkout", "req-lock");

        ResponseEntity<ProblemDetail> response = handler.handleTransientDatabaseFailure(
                new CannotAcquireLockException("could not obtain lock on row"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).isEqualTo("Database is temporarily busy; retry the request");
        assertThat(response.getBody().getProperties()).containsEntry("code", "SERVICE_UNAVAILABLE");
    }

    @Test
    void queryTimeoutMapsToServiceUnavailableWithoutSqlDetails() {
        MockHttpServletRequest request = requestWithId("/ledger/transactions", "req-timeout");

        ResponseEntity<ProblemDetail> response = handler.handleTransientDatabaseFailure(
                new QueryTimeoutException("canceling statement due to statement timeout; SQL [select * from ledger]"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).doesNotContain("SQL", "ledger", "statement timeout");
        assertThat(response.getBody().getProperties()).doesNotContainKeys("trace", "exception");
    }

    @Test
    void unexpectedExceptionResponseIsSanitizedAndLogged(CapturedOutput output) {
        MockHttpServletRequest request = requestWithId("/checkout", "req-unexpected");
        request.addHeader("Authorization", "Bearer raw-token-value");

        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new IllegalStateException(
                        "Authorization: Bearer raw-token-value; secret=sk_live_123; body={card=4111111111111111}"
                ),
                request
        );

        ProblemDetail body = response.getBody();
        String logs = output.getOut() + output.getErr();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.getDetail()).isEqualTo("Unexpected server error");
        assertThat(body.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(body.getProperties()).containsEntry("path", "/checkout");
        assertThat(body.getProperties()).containsEntry("requestId", "req-unexpected");
        assertThat(body.getProperties()).doesNotContainKeys("trace", "exception");
        assertThat(body.toString())
                .doesNotContain("raw-token-value", "sk_live_123", "4111111111111111", "Authorization");

        assertThat(logs)
                .contains(
                        "unexpected exception",
                        "path=/checkout",
                        "requestId=req-unexpected",
                        "exceptionType=java.lang.IllegalStateException"
                )
                .doesNotContain("raw-token-value", "sk_live_123", "4111111111111111", "Authorization");
    }

    private MockHttpServletRequest requestWithId(String path, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
