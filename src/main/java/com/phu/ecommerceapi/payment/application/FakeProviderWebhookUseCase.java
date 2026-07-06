package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.api.FakeProviderWebhookRequest;
import com.phu.ecommerceapi.payment.api.ProviderWebhookResponse;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.ProviderWebhookEventRecord;
import com.phu.ecommerceapi.payment.infrastructure.ProviderWebhookEventRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecord;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class FakeProviderWebhookUseCase {

    private static final String PROVIDER_NAME = "fake";
    private static final String WEBHOOK_RESOURCE_TYPE = "PROVIDER_WEBHOOK";

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ProviderWebhookEventRepository eventRepository;
    private final PaymentRecordRepository paymentRepository;
    private final RefundRecordRepository refundRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final RefundAttemptService refundAttemptService;
    private final AuditEventRecorder auditEventRecorder;

    public FakeProviderWebhookUseCase(
            ObjectMapper objectMapper,
            AppProperties appProperties,
            ProviderWebhookEventRepository eventRepository,
            PaymentRecordRepository paymentRepository,
            RefundRecordRepository refundRepository,
            PaymentAttemptService paymentAttemptService,
            RefundAttemptService refundAttemptService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.eventRepository = eventRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
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

        EventRegistration registration = registerEvent(request, payloadHash, requestBody);
        if (registration.response() != null) {
            return registration.response();
        }

        ProviderWebhookEventRecord event = registration.event();
        if (event.getProcessingStatus() != ProviderWebhookProcessingStatus.RECEIVED) {
            return duplicateResponse(event);
        }

        return processEvent(event, request);
    }

    private EventRegistration registerEvent(
            FakeProviderWebhookRequest request,
            String payloadHash,
            String requestBody
    ) {
        UUID eventId = UUID.randomUUID();
        int insertedRows = eventRepository.insertReceived(
                eventId,
                PROVIDER_NAME,
                request.eventId(),
                request.eventType().name(),
                payloadHash,
                requestBody,
                OffsetDateTime.now()
        );

        ProviderWebhookEventRecord existingEvent = eventRepository
                .findByProviderNameAndProviderEventId(PROVIDER_NAME, request.eventId())
                .orElseThrow(() -> new IllegalStateException("Provider webhook event was not persisted"));

        if (insertedRows == 0) {
            if (!existingEvent.hasPayloadHash(payloadHash)) {
                recordAudit("PROVIDER_WEBHOOK_PAYLOAD_CONFLICT", request.eventId(), "payload hash mismatch");
                return EventRegistration.response(new FakeProviderWebhookResult(
                        HttpStatus.CONFLICT.value(),
                        new ProviderWebhookResponse(
                                request.eventId(),
                                "REJECTED",
                                "Provider event id was reused with a different payload"
                        )
                ));
            }
        }
        return EventRegistration.event(existingEvent);
    }

    private FakeProviderWebhookResult processEvent(
            ProviderWebhookEventRecord event,
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
            ProviderWebhookEventRecord event,
            FakeProviderWebhookRequest request,
            boolean succeeded
    ) {
        Optional<PaymentRecord> payment = findPayment(request);
        if (payment.isEmpty()) {
            return rejected(event, "Payment not found for provider webhook event");
        }

        PaymentRecord paymentRecord = payment.get();
        if (paymentRecord.getStatus() != PaymentStatus.PENDING) {
            return ignored(
                    event,
                    "Payment event ignored because payment is " + paymentRecord.getStatus()
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
        paymentAttemptService.completeAttempt(paymentRecord.getId(), providerResult, null);
        event.markProcessed("Payment webhook applied to payment " + paymentRecord.getId());
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", event.getProviderEventId(), event.getProcessingMessage());
        return response(HttpStatus.OK, event);
    }

    private FakeProviderWebhookResult processRefundEvent(
            ProviderWebhookEventRecord event,
            FakeProviderWebhookRequest request,
            boolean succeeded
    ) {
        Optional<RefundRecord> refund = findRefund(request);
        if (refund.isEmpty()) {
            return rejected(event, "Refund not found for provider webhook event");
        }

        RefundRecord refundRecord = refund.get();
        if (refundRecord.getStatus() != RefundStatus.PENDING) {
            return ignored(
                    event,
                    "Refund event ignored because refund is " + refundRecord.getStatus()
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
        refundAttemptService.completeAttempt(refundRecord.getId(), providerResult, null);
        event.markProcessed("Refund webhook applied to refund " + refundRecord.getId());
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", event.getProviderEventId(), event.getProcessingMessage());
        return response(HttpStatus.OK, event);
    }

    private Optional<PaymentRecord> findPayment(FakeProviderWebhookRequest request) {
        if (request.paymentId() != null) {
            return paymentRepository.findById(request.paymentId());
        }
        if (request.providerPaymentId() != null) {
            return paymentRepository.findByProviderPaymentId(request.providerPaymentId());
        }
        return Optional.empty();
    }

    private Optional<RefundRecord> findRefund(FakeProviderWebhookRequest request) {
        if (request.refundId() != null) {
            return refundRepository.findById(request.refundId());
        }
        if (request.providerRefundId() != null) {
            return refundRepository.findByProviderRefundId(request.providerRefundId());
        }
        return Optional.empty();
    }

    private FakeProviderWebhookResult duplicateResponse(ProviderWebhookEventRecord event) {
        ProviderWebhookResponse response = new ProviderWebhookResponse(
                event.getProviderEventId(),
                "DUPLICATE",
                "Provider webhook event was already received"
        );
        return new FakeProviderWebhookResult(HttpStatus.OK.value(), response);
    }

    private FakeProviderWebhookResult ignored(ProviderWebhookEventRecord event, String message) {
        event.markIgnored(message);
        recordAudit("PROVIDER_WEBHOOK_IGNORED", event.getProviderEventId(), message);
        return response(HttpStatus.OK, event);
    }

    private FakeProviderWebhookResult rejected(ProviderWebhookEventRecord event, String message) {
        event.markRejected(message);
        recordAudit("PROVIDER_WEBHOOK_REJECTED", event.getProviderEventId(), message);
        return response(HttpStatus.ACCEPTED, event);
    }

    private FakeProviderWebhookResult response(HttpStatus httpStatus, ProviderWebhookEventRecord event) {
        ProviderWebhookResponse response = new ProviderWebhookResponse(
                event.getProviderEventId(),
                event.getProcessingStatus().name(),
                event.getProcessingMessage()
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

    private record EventRegistration(
            ProviderWebhookEventRecord event,
            FakeProviderWebhookResult response
    ) {

        static EventRegistration event(ProviderWebhookEventRecord event) {
            return new EventRegistration(event, null);
        }

        static EventRegistration response(FakeProviderWebhookResult response) {
            return new EventRegistration(null, response);
        }
    }
}
