package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.config.AppProperties;
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

    private final AppProperties appProperties;
    private final ProviderWebhookPersistencePort webhookPersistence;
    private final PaymentAttemptPersistencePort paymentAttemptPersistence;
    private final RefundAttemptPersistencePort refundAttemptPersistence;
    private final PaymentAttemptService paymentAttemptService;
    private final RefundAttemptService refundAttemptService;
    private final AuditEventRecorder auditEventRecorder;

    public FakeProviderWebhookUseCase(
            AppProperties appProperties,
            ProviderWebhookPersistencePort webhookPersistence,
            PaymentAttemptPersistencePort paymentAttemptPersistence,
            RefundAttemptPersistencePort refundAttemptPersistence,
            PaymentAttemptService paymentAttemptService,
            RefundAttemptService refundAttemptService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.appProperties = appProperties;
        this.webhookPersistence = webhookPersistence;
        this.paymentAttemptPersistence = paymentAttemptPersistence;
        this.refundAttemptPersistence = refundAttemptPersistence;
        this.paymentAttemptService = paymentAttemptService;
        this.refundAttemptService = refundAttemptService;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public FakeProviderWebhookResult handle(FakeProviderWebhookCommand command) {
        if (!isValidSecret(command.webhookSecret())) {
            return invalidSecretResponse();
        }
        String payloadHash = hash(command.requestBody());

        ProviderWebhookRegistration registration = registerEvent(command, payloadHash);
        if (!registration.payloadMatched()) {
            recordAudit("PROVIDER_WEBHOOK_PAYLOAD_CONFLICT", command.eventId(), "payload hash mismatch");
            return new FakeProviderWebhookResult(
                    HttpStatus.CONFLICT.value(),
                    new ProviderWebhookHandlingResponse(
                            command.eventId(),
                            "REJECTED",
                            "Provider event id was reused with a different payload"
                    )
            );
        }

        ProviderWebhookEventView event = registration.event();
        if (event.processingStatus() != ProviderWebhookProcessingStatus.RECEIVED) {
            return duplicateResponse(event);
        }

        return processEvent(event, command);
    }

    private ProviderWebhookRegistration registerEvent(
            FakeProviderWebhookCommand command,
            String payloadHash
    ) {
        return webhookPersistence.registerReceived(new ProviderWebhookRegistrationCommand(
                PROVIDER_NAME,
                command.eventId(),
                command.eventType(),
                payloadHash,
                command.requestBody(),
                OffsetDateTime.now()
        ));
    }

    private FakeProviderWebhookResult processEvent(
            ProviderWebhookEventView event,
            FakeProviderWebhookCommand command
    ) {
        return switch (command.eventType()) {
            case PAYMENT_SUCCEEDED -> processPaymentEvent(event, command, true);
            case PAYMENT_FAILED -> processPaymentEvent(event, command, false);
            case REFUND_SUCCEEDED -> processRefundEvent(event, command, true);
            case REFUND_FAILED -> processRefundEvent(event, command, false);
        };
    }

    private FakeProviderWebhookResult processPaymentEvent(
            ProviderWebhookEventView event,
            FakeProviderWebhookCommand command,
            boolean succeeded
    ) {
        Optional<PaymentWebhookAttempt> payment = findPayment(command);
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
                        command.providerPaymentId(),
                        "provider payment id"
                ), message(command, "Fake payment webhook succeeded"))
                : PaymentProviderResult.failed(
                        requireText(command.providerPaymentId(), "provider payment id"),
                        failureCode(command),
                        message(command, "Fake payment webhook failed")
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
            FakeProviderWebhookCommand command,
            boolean succeeded
    ) {
        Optional<RefundWebhookAttempt> refund = findRefund(command);
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
                        command.providerRefundId(),
                        "provider refund id"
                ), message(command, "Fake refund webhook succeeded"))
                : PaymentRefundProviderResult.failed(
                        requireText(command.providerRefundId(), "provider refund id"),
                        failureCode(command),
                        message(command, "Fake refund webhook failed")
                );
        refundAttemptService.completeAttempt(refundAttempt.refundId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Refund webhook applied to refund " + refundAttempt.refundId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed.providerEventId(), processed.processingMessage());
        return response(HttpStatus.OK, processed);
    }

    private Optional<PaymentWebhookAttempt> findPayment(FakeProviderWebhookCommand command) {
        return paymentAttemptPersistence.findForProviderWebhook(command.paymentId(), command.providerPaymentId());
    }

    private Optional<RefundWebhookAttempt> findRefund(FakeProviderWebhookCommand command) {
        return refundAttemptPersistence.findForProviderWebhook(command.refundId(), command.providerRefundId());
    }

    private FakeProviderWebhookResult duplicateResponse(ProviderWebhookEventView event) {
        ProviderWebhookHandlingResponse response = new ProviderWebhookHandlingResponse(
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
        ProviderWebhookHandlingResponse response = new ProviderWebhookHandlingResponse(
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
                new ProviderWebhookHandlingResponse(
                        "unknown",
                        "REJECTED",
                        "Invalid provider webhook secret"
                )
        );
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

    private String failureCode(FakeProviderWebhookCommand command) {
        return command.failureCode() == null ? "provider_webhook_failed" : command.failureCode();
    }

    private String message(FakeProviderWebhookCommand command, String fallback) {
        return command.message() == null ? fallback : command.message();
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
