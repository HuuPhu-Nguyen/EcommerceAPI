package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import org.springframework.beans.factory.ObjectProvider;
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
public class StripeProviderWebhookUseCase {

    private static final String PROVIDER_CODE = "stripe";
    private static final String WEBHOOK_RESOURCE_TYPE = "PROVIDER_WEBHOOK";
    private static final String UNKNOWN_EVENT_ID = "unknown";

    private final StripeWebhookEventParser stripeWebhookEventParser;
    private final ObjectProvider<StripeProviderReadPort> stripeProviderReadPort;
    private final ProviderWebhookPersistencePort webhookPersistence;
    private final PaymentAttemptPersistencePort paymentAttemptPersistence;
    private final RefundAttemptPersistencePort refundAttemptPersistence;
    private final PaymentAttemptService paymentAttemptService;
    private final RefundAttemptService refundAttemptService;
    private final AuditEventRecorder auditEventRecorder;

    public StripeProviderWebhookUseCase(
            StripeWebhookEventParser stripeWebhookEventParser,
            ObjectProvider<StripeProviderReadPort> stripeProviderReadPort,
            ProviderWebhookPersistencePort webhookPersistence,
            PaymentAttemptPersistencePort paymentAttemptPersistence,
            RefundAttemptPersistencePort refundAttemptPersistence,
            PaymentAttemptService paymentAttemptService,
            RefundAttemptService refundAttemptService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.stripeWebhookEventParser = stripeWebhookEventParser;
        this.stripeProviderReadPort = stripeProviderReadPort;
        this.webhookPersistence = webhookPersistence;
        this.paymentAttemptPersistence = paymentAttemptPersistence;
        this.refundAttemptPersistence = refundAttemptPersistence;
        this.paymentAttemptService = paymentAttemptService;
        this.refundAttemptService = refundAttemptService;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public ProviderWebhookResult handle(String signatureHeader, String requestBody) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return invalidSignatureResponse("missing Stripe webhook signature");
        }

        StripeWebhookEvent stripeEvent;
        try {
            stripeEvent = stripeWebhookEventParser.parseAndVerify(requestBody, signatureHeader);
        } catch (StripeWebhookSignatureException exception) {
            return invalidSignatureResponse("invalid Stripe webhook signature");
        }

        String payloadHash = hash(requestBody);
        ProviderWebhookRegistration registration = registerEvent(stripeEvent, requestBody, payloadHash);
        if (!registration.payloadMatched()) {
            recordAudit(
                    "PROVIDER_WEBHOOK_PAYLOAD_CONFLICT",
                    stripeEvent.eventId(),
                    stripeEvent.providerEventType(),
                    "REJECTED",
                    "payload hash mismatch"
            );
            return new ProviderWebhookResult(
                    HttpStatus.CONFLICT.value(),
                    new ProviderWebhookHandlingResponse(
                            stripeEvent.eventId(),
                            ProviderWebhookProcessingStatus.REJECTED.name(),
                            "Provider event id was reused with a different payload"
                    )
            );
        }

        ProviderWebhookEventView event = registration.event();
        if (event.processingStatus() != ProviderWebhookProcessingStatus.RECEIVED) {
            return duplicateResponse(event);
        }

        return processEvent(event, stripeEvent);
    }

    private ProviderWebhookRegistration registerEvent(
            StripeWebhookEvent stripeEvent,
            String requestBody,
            String payloadHash
    ) {
        return webhookPersistence.registerReceived(new ProviderWebhookRegistrationCommand(
                PROVIDER_CODE,
                stripeEvent.eventId(),
                stripeEvent.eventType(),
                payloadHash,
                requestBody,
                OffsetDateTime.now(),
                stripeEvent.createdAt(),
                stripeEvent.providerEventType(),
                stripeEvent.providerObjectId(),
                stripeEvent.providerObjectType()
        ));
    }

    private ProviderWebhookResult processEvent(
            ProviderWebhookEventView event,
            StripeWebhookEvent stripeEvent
    ) {
        return switch (stripeEvent.eventType()) {
            case PAYMENT_SUCCEEDED -> processPaymentSucceeded(event, stripeEvent);
            case PAYMENT_FAILED -> processPaymentFailed(event, stripeEvent);
            case REFUND_SUCCEEDED -> processRefundSucceeded(event, stripeEvent);
            case REFUND_FAILED -> processRefundFailed(event, stripeEvent);
            case UNSUPPORTED -> ignored(
                    event,
                    "Stripe webhook event type is unsupported: " + stripeEvent.providerEventType()
            );
        };
    }

    private ProviderWebhookResult processPaymentSucceeded(
            ProviderWebhookEventView event,
            StripeWebhookEvent stripeEvent
    ) {
        String providerPaymentId = requireText(stripeEvent.providerPaymentId(), "Stripe PaymentIntent id");
        Optional<PaymentWebhookAttempt> payment = findPayment(stripeEvent, providerPaymentId);
        if (payment.isEmpty()) {
            return rejected(event, "Payment not found for Stripe PaymentIntent");
        }

        PaymentWebhookAttempt paymentAttempt = payment.get();
        if (isPaidOrRefunded(paymentAttempt.status())) {
            return ignored(event, "Payment event ignored because payment is " + paymentAttempt.status());
        }

        Optional<StripePaymentIntentSnapshot> current = fetchPaymentIntent(providerPaymentId);
        if (current.isEmpty()) {
            return reconciliationRequired(event, "Stripe PaymentIntent current state could not be read");
        }
        if (!current.get().isSucceeded()) {
            return reconciliationRequired(
                    event,
                    "Stripe PaymentIntent current status is " + current.get().status()
            );
        }

        return completePaymentSucceeded(event, paymentAttempt, current.get());
    }

    private ProviderWebhookResult processPaymentFailed(
            ProviderWebhookEventView event,
            StripeWebhookEvent stripeEvent
    ) {
        String providerPaymentId = requireText(stripeEvent.providerPaymentId(), "Stripe PaymentIntent id");
        Optional<PaymentWebhookAttempt> payment = findPayment(stripeEvent, providerPaymentId);
        if (payment.isEmpty()) {
            return rejected(event, "Payment not found for Stripe PaymentIntent");
        }

        PaymentWebhookAttempt paymentAttempt = payment.get();
        if (isPaidOrRefunded(paymentAttempt.status())) {
            return ignored(event, "Payment event ignored because payment is " + paymentAttempt.status());
        }

        Optional<StripePaymentIntentSnapshot> current = fetchPaymentIntent(providerPaymentId);
        if (current.isEmpty()) {
            return reconciliationRequired(event, "Stripe PaymentIntent current state could not be read");
        }
        StripePaymentIntentSnapshot currentState = current.get();
        if (currentState.isSucceeded()) {
            return completePaymentSucceeded(event, paymentAttempt, currentState);
        }
        if (!currentState.isFailedOrCanceled()) {
            return reconciliationRequired(
                    event,
                    "Stripe PaymentIntent current status is " + currentState.status()
            );
        }
        if (paymentAttempt.status() == PaymentStatus.FAILED) {
            return ignored(event, "Payment event ignored because payment is FAILED");
        }

        PaymentProviderResult providerResult = PaymentProviderResult.failed(
                currentState.providerPaymentId(),
                currentState.status(),
                firstText(currentState.failureCode(), stripeEvent.failureCode(), "stripe_payment_failed"),
                firstText(currentState.message(), stripeEvent.message(), "Stripe payment failed")
        );
        paymentAttemptService.completeAttempt(paymentAttempt.paymentId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Stripe payment webhook applied to payment " + paymentAttempt.paymentId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed);
        return response(HttpStatus.OK, processed);
    }

    private ProviderWebhookResult completePaymentSucceeded(
            ProviderWebhookEventView event,
            PaymentWebhookAttempt paymentAttempt,
            StripePaymentIntentSnapshot currentState
    ) {
        PaymentProviderResult providerResult = PaymentProviderResult.succeeded(
                currentState.providerPaymentId(),
                currentState.status(),
                firstText(currentState.message(), "Stripe payment succeeded")
        );
        paymentAttemptService.completeAttempt(paymentAttempt.paymentId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Stripe payment webhook applied to payment " + paymentAttempt.paymentId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed);
        return response(HttpStatus.OK, processed);
    }

    private ProviderWebhookResult processRefundSucceeded(
            ProviderWebhookEventView event,
            StripeWebhookEvent stripeEvent
    ) {
        String providerRefundId = requireText(stripeEvent.providerRefundId(), "Stripe refund id");
        Optional<RefundWebhookAttempt> refund = findRefund(stripeEvent, providerRefundId);
        if (refund.isEmpty()) {
            return rejected(event, "Refund not found for Stripe refund");
        }

        RefundWebhookAttempt refundAttempt = refund.get();
        if (refundAttempt.status() == RefundStatus.SUCCEEDED) {
            return ignored(event, "Refund event ignored because refund is SUCCEEDED");
        }
        if (refundAttempt.status() == RefundStatus.FAILED) {
            return reconciliationRequired(event, "Stripe refund is locally FAILED but provider event succeeded");
        }

        Optional<StripeRefundSnapshot> current = fetchRefund(providerRefundId);
        if (current.isEmpty()) {
            return reconciliationRequired(event, "Stripe refund current state could not be read");
        }
        if (!current.get().isSucceeded()) {
            return reconciliationRequired(event, "Stripe refund current status is " + current.get().status());
        }

        return completeRefundSucceeded(event, refundAttempt, current.get());
    }

    private ProviderWebhookResult processRefundFailed(
            ProviderWebhookEventView event,
            StripeWebhookEvent stripeEvent
    ) {
        String providerRefundId = requireText(stripeEvent.providerRefundId(), "Stripe refund id");
        Optional<RefundWebhookAttempt> refund = findRefund(stripeEvent, providerRefundId);
        if (refund.isEmpty()) {
            return rejected(event, "Refund not found for Stripe refund");
        }

        RefundWebhookAttempt refundAttempt = refund.get();
        Optional<StripeRefundSnapshot> current = fetchRefund(providerRefundId);
        if (current.isEmpty()) {
            return reconciliationRequired(event, "Stripe refund current state could not be read");
        }
        StripeRefundSnapshot currentState = current.get();
        if (currentState.isSucceeded()) {
            if (refundAttempt.status() == RefundStatus.SUCCEEDED) {
                return ignored(event, "Refund event ignored because refund is SUCCEEDED");
            }
            if (refundAttempt.status() == RefundStatus.FAILED) {
                return reconciliationRequired(
                        event,
                        "Stripe refund is locally FAILED but provider current status is succeeded"
                );
            }
            return completeRefundSucceeded(event, refundAttempt, currentState);
        }
        if (!currentState.isFailedOrCanceled()) {
            return reconciliationRequired(event, "Stripe refund current status is " + currentState.status());
        }
        if (refundAttempt.status() == RefundStatus.FAILED) {
            return ignored(event, "Refund event ignored because refund is FAILED");
        }
        if (refundAttempt.status() == RefundStatus.SUCCEEDED) {
            return reconciliationRequired(
                    event,
                    "Stripe refund is locally SUCCEEDED but provider current status is " + currentState.status()
            );
        }

        PaymentRefundProviderResult providerResult = PaymentRefundProviderResult.failed(
                currentState.providerRefundId(),
                firstText(currentState.failureCode(), stripeEvent.failureCode(), "stripe_refund_failed"),
                firstText(currentState.message(), stripeEvent.message(), "Stripe refund failed")
        );
        refundAttemptService.completeAttempt(refundAttempt.refundId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Stripe refund webhook applied to refund " + refundAttempt.refundId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed);
        return response(HttpStatus.OK, processed);
    }

    private ProviderWebhookResult completeRefundSucceeded(
            ProviderWebhookEventView event,
            RefundWebhookAttempt refundAttempt,
            StripeRefundSnapshot currentState
    ) {
        PaymentRefundProviderResult providerResult = PaymentRefundProviderResult.succeeded(
                currentState.providerRefundId(),
                firstText(currentState.message(), "Stripe refund succeeded")
        );
        refundAttemptService.completeAttempt(refundAttempt.refundId(), providerResult, null);
        ProviderWebhookEventView processed = webhookPersistence.markProcessed(
                event.eventId(),
                "Stripe refund webhook applied to refund " + refundAttempt.refundId()
        );
        recordAudit("PROVIDER_WEBHOOK_PROCESSED", processed);
        return response(HttpStatus.OK, processed);
    }

    private Optional<PaymentWebhookAttempt> findPayment(
            StripeWebhookEvent stripeEvent,
            String providerPaymentId
    ) {
        return paymentAttemptPersistence.findForProviderWebhook(
                PROVIDER_CODE,
                stripeEvent.paymentId(),
                providerPaymentId
        );
    }

    private Optional<RefundWebhookAttempt> findRefund(
            StripeWebhookEvent stripeEvent,
            String providerRefundId
    ) {
        return refundAttemptPersistence.findForProviderWebhook(
                PROVIDER_CODE,
                stripeEvent.refundId(),
                providerRefundId
        );
    }

    private Optional<StripePaymentIntentSnapshot> fetchPaymentIntent(String providerPaymentId) {
        StripeProviderReadPort readPort = stripeProviderReadPort.getIfAvailable();
        if (readPort == null) {
            return Optional.empty();
        }
        try {
            return readPort.fetchPaymentIntent(providerPaymentId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<StripeRefundSnapshot> fetchRefund(String providerRefundId) {
        StripeProviderReadPort readPort = stripeProviderReadPort.getIfAvailable();
        if (readPort == null) {
            return Optional.empty();
        }
        try {
            return readPort.fetchRefund(providerRefundId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private ProviderWebhookResult duplicateResponse(ProviderWebhookEventView event) {
        ProviderWebhookHandlingResponse response = new ProviderWebhookHandlingResponse(
                event.providerEventId(),
                "DUPLICATE",
                "Provider webhook event was already received"
        );
        return new ProviderWebhookResult(HttpStatus.OK.value(), response);
    }

    private ProviderWebhookResult ignored(ProviderWebhookEventView event, String message) {
        ProviderWebhookEventView ignored = webhookPersistence.markIgnored(event.eventId(), message);
        recordAudit("PROVIDER_WEBHOOK_IGNORED", ignored);
        return response(HttpStatus.OK, ignored);
    }

    private ProviderWebhookResult rejected(ProviderWebhookEventView event, String message) {
        ProviderWebhookEventView rejected = webhookPersistence.markRejected(event.eventId(), message);
        recordAudit("PROVIDER_WEBHOOK_REJECTED", rejected);
        return response(HttpStatus.ACCEPTED, rejected);
    }

    private ProviderWebhookResult reconciliationRequired(ProviderWebhookEventView event, String message) {
        ProviderWebhookEventView reconciliationRequired = webhookPersistence.markReconciliationRequired(
                event.eventId(),
                message
        );
        recordAudit("PROVIDER_WEBHOOK_RECONCILIATION_REQUIRED", reconciliationRequired);
        return response(HttpStatus.ACCEPTED, reconciliationRequired);
    }

    private ProviderWebhookResult invalidSignatureResponse(String message) {
        recordAudit(
                "PROVIDER_WEBHOOK_AUTH_FAILED",
                UNKNOWN_EVENT_ID,
                "unknown",
                ProviderWebhookProcessingStatus.REJECTED.name(),
                message
        );
        return new ProviderWebhookResult(
                HttpStatus.FORBIDDEN.value(),
                new ProviderWebhookHandlingResponse(
                        UNKNOWN_EVENT_ID,
                        ProviderWebhookProcessingStatus.REJECTED.name(),
                        "Invalid Stripe webhook signature"
                )
        );
    }

    private ProviderWebhookResult response(HttpStatus httpStatus, ProviderWebhookEventView event) {
        ProviderWebhookHandlingResponse response = new ProviderWebhookHandlingResponse(
                event.providerEventId(),
                event.processingStatus().name(),
                event.processingMessage()
        );
        return new ProviderWebhookResult(httpStatus.value(), response);
    }

    private boolean isPaidOrRefunded(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.REFUNDED;
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

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void recordAudit(String action, ProviderWebhookEventView event) {
        recordAudit(
                action,
                event.providerEventId(),
                event.eventType().name(),
                event.processingStatus().name(),
                event.processingMessage()
        );
    }

    private void recordAudit(
            String action,
            String providerEventId,
            String eventType,
            String status,
            String message
    ) {
        auditEventRecorder.record(new AuditEventCommand(
                null,
                action,
                WEBHOOK_RESOURCE_TYPE,
                providerEventId,
                "provider=%s; eventId=%s; eventType=%s; status=%s; message=%s".formatted(
                        PROVIDER_CODE,
                        providerEventId,
                        eventType,
                        status,
                        message
                )
        ));
    }
}
