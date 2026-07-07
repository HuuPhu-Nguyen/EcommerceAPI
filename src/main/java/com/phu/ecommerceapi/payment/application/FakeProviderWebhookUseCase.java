package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.api.FakeProviderWebhookRequest;
import com.phu.ecommerceapi.payment.api.ProviderWebhookResponse;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class FakeProviderWebhookUseCase {

    private static final String PROVIDER_NAME = "fake";
    private static final String WEBHOOK_RESOURCE_TYPE = "PROVIDER_WEBHOOK";

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ProviderWebhookPersistencePort webhookPersistence;
    private final PaymentAttemptPersistencePort paymentAttemptPersistence;
    private final RefundAttemptPersistencePort refundAttemptPersistence;
    private final PaymentAttemptService paymentAttemptService;
    private final RefundAttemptService refundAttemptService;
    private final AuditEventRecorder auditEventRecorder;

    public FakeProviderWebhookUseCase(
            ObjectMapper objectMapper,
            AppProperties appProperties,
            ProviderWebhookPersistencePort webhookPersistence,
            PaymentAttemptPersistencePort paymentAttemptPersistence,
            RefundAttemptPersistencePort refundAttemptPersistence,
            PaymentAttemptService paymentAttemptService,
            RefundAttemptService refundAttemptService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.webhookPersistence = webhookPersistence;
        this.paymentAttemptPersistence = paymentAttemptPersistence;
        this.refundAttemptPersistence = refundAttemptPersistence;
        this.paymentAttemptService = paymentAttemptService;
        this.refundAttemptService = refundAttemptService;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public FakeProviderWebhookResult handle(String webhookSecret, String requestBody) {
        if (!isValidSecret(webhookSecret)) {
            return invalidSecretResponse();
        }
        FakeProviderWebhookRequest request = parseRequest(requestBody);
        String payloadHash = hash(requestBody);

        ProviderWebhookRegistration registration = registerEvent(request, payloadHash, requestBody);
        if (!registration.payloadMatched()) {
            recordAudit("PROVIDER_WEBHOOK_PAYLOAD_CONFLICT", request.eventId(), "payload hash mismatch");
            return new FakeProviderWebhookResult(
                    HttpStatus.CONFLICT.value(),
                    new ProviderWebhookResponse(
                            request.eventId(),
                            "REJECTED",
                            "Provider event id was reused with a different payload"
                    )
            );
        }

        ProviderWebhookEventView event = registration.event();
        if (event.processingStatus() != ProviderWebhookProcessingStatus.RECEIVED) {
            return duplicateResponse(event);
        }

        return processEvent(event, request);
    }

    private ProviderWebhookRegistration registerEvent(
            FakeProviderWebhookRequest request,
            String payloadHash,
            String requestBody
    ) {
        return webhookPersistence.registerReceived(new ProviderWebhookRegistrationCommand(
                PROVIDER_NAME,
                request.eventId(),
                request.eventType(),
                payloadHash,
                requestBody,
                OffsetDateTime.now()
        ));
    }

    private FakeProviderWebhookResult processEvent(
            ProviderWebhookEventView event,
            FakeProviderWebhookRequest request
    ) {
        return switch (request.eventType()) {
            case PAYMENT_SUCCEEDED -> processPaymentEvent(event, request, true);
            case PAYMENT_FAILED -> processPaymentEvent(event, request, false);
            case REFUND_SUCCEEDED -> processRefundEvent(event, request, true);
            case REFUND_FAILED -> processRefundEvent(event, request, false);
        };
    }

    private FakeProviderWebhookResult processPaymentEvent(
            ProviderWebhookEventView event,
            FakeProviderWebhookRequest request,
            boolean succeeded
    ) {
        Optional<PaymentWebhookAttempt> payment = findPayment(request);
        if (payment.isEmpty()) {
            return rejected(event, "Payment not found for provider webhook event");
        }

        PaymentWebhookAttempt paymentAttempt = payment.get();
        if (paymentAttempt.status() != PaymentStatus.PENDING) {
            return ignored(
                    event,
                    "Payment event ignored because payment is " + paymentAttempt.status()
            );
        }

        PaymentProviderResult providerResult = succeeded
                ? PaymentProviderResult.succeeded(requireText(
                        request.providerPaymentId(),
                        "provider payment id"
                ), message(request, "Fake payment webhook succeeded"))
                : PaymentProviderResult.failed(
                        requireText(request.providerPaymentId(), "provider payment id"),
                        failureCode(request),
                        message(request, "Fake payment webhook failed")
                );
        paymentAttemptService.completeAttempt(paymentAttempt.paymentId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Payment webhook applied to payment " + paymentAttempt.paymentId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed.providerEventId(), processed.processingMessage());
        return response(HttpStatus.OK, processed);
    }

    private FakeProviderWebhookResult processRefundEvent(
            ProviderWebhookEventView event,
            FakeProviderWebhookRequest request,
            boolean succeeded
    ) {
        Optional<RefundWebhookAttempt> refund = findRefund(request);
        if (refund.isEmpty()) {
            return rejected(event, "Refund not found for provider webhook event");
        }

        RefundWebhookAttempt refundAttempt = refund.get();
        if (refundAttempt.status() != RefundStatus.PENDING) {
            return ignored(
                    event,
                    "Refund event ignored because refund is " + refundAttempt.status()
            );
        }

        PaymentRefundProviderResult providerResult = succeeded
                ? PaymentRefundProviderResult.succeeded(requireText(
                        request.providerRefundId(),
                        "provider refund id"
                ), message(request, "Fake refund webhook succeeded"))
                : PaymentRefundProviderResult.failed(
                        requireText(request.providerRefundId(), "provider refund id"),
                        failureCode(request),
                        message(request, "Fake refund webhook failed")
                );
        refundAttemptService.completeAttempt(refundAttempt.refundId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Refund webhook applied to refund " + refundAttempt.refundId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed.providerEventId(), processed.processingMessage());
        return response(HttpStatus.OK, processed);
    }

    private Optional<PaymentWebhookAttempt> findPayment(FakeProviderWebhookRequest request) {
        return paymentAttemptPersistence.findForProviderWebhook(request.paymentId(), request.providerPaymentId());
    }

    private Optional<RefundWebhookAttempt> findRefund(FakeProviderWebhookRequest request) {
        return refundAttemptPersistence.findForProviderWebhook(request.refundId(), request.providerRefundId());
    }

    private FakeProviderWebhookResult duplicateResponse(ProviderWebhookEventView event) {
        ProviderWebhookResponse response = new ProviderWebhookResponse(
                event.providerEventId(),
                "DUPLICATE",
                "Provider webhook event was already received"
        );
        return new FakeProviderWebhookResult(HttpStatus.OK.value(), response);
    }

    private FakeProviderWebhookResult ignored(ProviderWebhookEventView event, String message) {
        ProviderWebhookEventView ignored = webhookPersistence.markIgnored(event.eventId(), message);
        recordAudit("PROVIDER_WEBHOOK_IGNORED", ignored.providerEventId(), message);
        return response(HttpStatus.OK, ignored);
    }

    private FakeProviderWebhookResult rejected(ProviderWebhookEventView event, String message) {
        ProviderWebhookEventView rejected = webhookPersistence.markRejected(event.eventId(), message);
        recordAudit("PROVIDER_WEBHOOK_REJECTED", rejected.providerEventId(), message);
        return response(HttpStatus.ACCEPTED, rejected);
    }

    private FakeProviderWebhookResult response(HttpStatus httpStatus, ProviderWebhookEventView event) {
        ProviderWebhookResponse response = new ProviderWebhookResponse(
                event.providerEventId(),
                event.processingStatus().name(),
                event.processingMessage()
        );
        return new FakeProviderWebhookResult(httpStatus.value(), response);
    }

    private boolean isValidSecret(String webhookSecret) {
        String expectedSecret = appProperties.fakeProvider().webhookSecret();
        return webhookSecret != null && webhookSecret.equals(expectedSecret);
    }

    private FakeProviderWebhookResult invalidSecretResponse() {
        recordAudit("PROVIDER_WEBHOOK_AUTH_FAILED", "unknown", "invalid fake provider webhook secret");
        return new FakeProviderWebhookResult(
                HttpStatus.FORBIDDEN.value(),
                new ProviderWebhookResponse(
                        "unknown",
                        "REJECTED",
                        "Invalid provider webhook secret"
                )
        );
    }

    private FakeProviderWebhookRequest parseRequest(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("provider webhook request body is required");
        }
        try {
            return objectMapper.readValue(requestBody, FakeProviderWebhookRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("provider webhook request body is invalid", exception);
        }
    }

    private String hash(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String failureCode(FakeProviderWebhookRequest request) {
        return request.failureCode() == null ? "provider_webhook_failed" : request.failureCode();
    }

    private String message(FakeProviderWebhookRequest request, String fallback) {
        return request.message() == null ? fallback : request.message();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private void recordAudit(String action, String resourceId, String details) {
        auditEventRecorder.record(new AuditEventCommand(
                null,
                action,
                WEBHOOK_RESOURCE_TYPE,
                resourceId,
                details
        ));
    }

}
