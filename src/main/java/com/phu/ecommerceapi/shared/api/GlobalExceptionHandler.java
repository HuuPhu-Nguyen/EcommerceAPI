package com.phu.ecommerceapi.shared.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DATABASE_CONFLICT_DETAIL = "Request conflicts with current resource state";
    private static final String TRANSIENT_DATABASE_DETAIL = "Database is temporarily busy; retry the request";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        return buildResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        ResponseEntity<ProblemDetail> response = buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request
        );

        List<FieldViolation> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldViolation)
                .toList();

        response.getBody().setProperty("fieldErrors", fieldErrors);
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "Authentication is required", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "Access is denied", request);
    }

    @ExceptionHandler({
            EntityNotFoundException.class,
            NoSuchElementException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleNotFound(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<ProblemDetail> handleDatabaseConflict(Exception exception, HttpServletRequest request) {
        logDatabaseException("conflict", exception, request);
        return buildResponse(
                HttpStatus.CONFLICT,
                ApiErrorCode.CONFLICT,
                DATABASE_CONFLICT_DETAIL,
                request
        );
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            PessimisticLockException.class,
            QueryTimeoutException.class,
            org.springframework.dao.QueryTimeoutException.class,
            TransactionTimedOutException.class
    })
    public ResponseEntity<ProblemDetail> handleTransientDatabaseFailure(
            Exception exception,
            HttpServletRequest request
    ) {
        logDatabaseException("transient", exception, request);
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.SERVICE_UNAVAILABLE,
                TRANSIENT_DATABASE_DETAIL,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "Unexpected server error",
                request
        );
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            HttpStatus status,
            ApiErrorCode code,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(toTitle(code));
        problem.setType(URI.create("urn:problem:" + toSlug(code)));
        problem.setProperty("code", code.name());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("requestId", requestId(request));

        return ResponseEntity.status(status).body(problem);
    }

    private void logDatabaseException(String category, Exception exception, HttpServletRequest request) {
        LOGGER.warn(
                "database exception category={} path={} requestId={} exceptionType={}",
                category,
                request.getRequestURI(),
                requestId(request),
                exception.getClass().getName()
        );
    }

    private FieldViolation toFieldViolation(FieldError fieldError) {
        return new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }
        return "unknown";
    }

    private String toTitle(ApiErrorCode code) {
        String lower = code.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String toSlug(ApiErrorCode code) {
        return code.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public record FieldViolation(String field, String message) {
    }
}
