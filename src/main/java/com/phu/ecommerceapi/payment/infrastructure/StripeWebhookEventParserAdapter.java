package com.phu.ecommerceapi.payment.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.StripeWebhookEvent;
import com.phu.ecommerceapi.payment.application.StripeWebhookEventParser;
import com.phu.ecommerceapi.payment.application.StripeWebhookSignatureException;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
class StripeWebhookEventParserAdapter implements StripeWebhookEventParser {

    private static final Duration SIGNATURE_TOLERANCE = Duration.ofMinutes(5);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    @Autowired
    StripeWebhookEventParserAdapter(ObjectMapper objectMapper, AppProperties appProperties) {
        this(objectMapper, appProperties, Clock.systemUTC());
    }

    StripeWebhookEventParserAdapter(
            ObjectMapper objectMapper,
            AppProperties appProperties,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Override
    public StripeWebhookEvent parseAndVerify(String requestBody, String signatureHeader) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("provider webhook request body is required");
        }
        verifySignature(requestBody, signatureHeader);
        return parseEvent(requestBody);
    }

    private void verifySignature(String requestBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new StripeWebhookSignatureException("Stripe webhook signature is required");
        }
        String webhookSecret = appProperties.stripe().webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new StripeWebhookSignatureException("Stripe webhook secret is not configured");
        }

        StripeSignature signature = parseSignatureHeader(signatureHeader);
        assertTimestampWithinTolerance(signature.timestamp());
        String expectedSignature = hmacHex(signature.timestamp() + "." + requestBody, webhookSecret);
        boolean matches = signature.signatures()
                .stream()
                .anyMatch(candidate -> constantTimeEquals(expectedSignature, candidate));
        if (!matches) {
            throw new StripeWebhookSignatureException("Stripe webhook signature is invalid");
        }
    }

    private StripeSignature parseSignatureHeader(String signatureHeader) {
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String token : signatureHeader.split(",")) {
            String[] parts = token.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String name = parts[0].trim();
            String value = parts[1].trim();
            if ("t".equals(name)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException exception) {
                    throw new StripeWebhookSignatureException("Stripe webhook timestamp is invalid");
                }
            } else if ("v1".equals(name) && !value.isBlank()) {
                signatures.add(value.toLowerCase(Locale.ROOT));
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new StripeWebhookSignatureException("Stripe webhook signature header is invalid");
        }
        return new StripeSignature(timestamp, List.copyOf(signatures));
    }

    private void assertTimestampWithinTolerance(long timestamp) {
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > SIGNATURE_TOLERANCE.toSeconds()) {
            throw new StripeWebhookSignatureException("Stripe webhook timestamp is outside tolerance");
        }
    }

    private String hmacHex(String signedPayload, String webhookSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Stripe webhook signature verification is unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String expectedSignature, String candidateSignature) {
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                candidateSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private StripeWebhookEvent parseEvent(String requestBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(requestBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("provider webhook request body is invalid", exception);
        }

        String eventId = requiredText(text(root, "id"), "Stripe event id");
        String providerEventType = requiredText(text(root, "type"), "Stripe event type");
        JsonNode object = root.path("data").path("object");
        JsonNode refundObject = refundObject(providerEventType, object);
        String objectStatus = objectStatus(providerEventType, object, refundObject);
        ProviderWebhookEventType eventType = eventType(providerEventType, objectStatus);

        String providerObjectId = text(object, "id");
        String providerObjectType = text(object, "object");
        String providerPaymentId = providerPaymentId(providerEventType, object);
        String providerRefundId = providerRefundId(providerEventType, object, refundObject);

        return new StripeWebhookEvent(
                eventId,
                eventType,
                eventCreatedAt(root),
                providerEventType,
                providerObjectId,
                providerObjectType,
                paymentId(providerEventType, object),
                refundId(providerEventType, object, refundObject),
                providerPaymentId,
                providerRefundId,
                objectStatus,
                failureCode(providerEventType, object, refundObject, objectStatus),
                message(eventType, objectStatus)
        );
    }

    private ProviderWebhookEventType eventType(String providerEventType, String objectStatus) {
        return switch (providerEventType) {
            case "payment_intent.succeeded" -> ProviderWebhookEventType.PAYMENT_SUCCEEDED;
            case "payment_intent.payment_failed", "payment_intent.canceled" ->
                    ProviderWebhookEventType.PAYMENT_FAILED;
            case "refund.updated", "charge.refunded" -> refundEventType(objectStatus);
            default -> ProviderWebhookEventType.UNSUPPORTED;
        };
    }

    private ProviderWebhookEventType refundEventType(String status) {
        String normalized = normalizeOptional(status);
        return switch (normalized) {
            case "succeeded" -> ProviderWebhookEventType.REFUND_SUCCEEDED;
            case "failed", "canceled" -> ProviderWebhookEventType.REFUND_FAILED;
            default -> ProviderWebhookEventType.UNSUPPORTED;
        };
    }

    private OffsetDateTime eventCreatedAt(JsonNode root) {
        JsonNode created = root.get("created");
        if (created == null || !created.canConvertToLong()) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(created.asLong()), ZoneOffset.UTC);
    }

    private String providerPaymentId(String providerEventType, JsonNode object) {
        if (providerEventType.startsWith("payment_intent.")) {
            return text(object, "id");
        }
        return text(object, "payment_intent");
    }

    private String providerRefundId(String providerEventType, JsonNode object, JsonNode refundObject) {
        if ("refund.updated".equals(providerEventType)) {
            return text(object, "id");
        }
        if ("charge.refunded".equals(providerEventType)) {
            return text(refundObject, "id");
        }
        return null;
    }

    private UUID paymentId(String providerEventType, JsonNode object) {
        if (!providerEventType.startsWith("payment_intent.")) {
            return null;
        }
        return metadataUuid(object.path("metadata"), "internalPaymentId");
    }

    private UUID refundId(String providerEventType, JsonNode object, JsonNode refundObject) {
        if ("refund.updated".equals(providerEventType)) {
            return metadataUuid(object.path("metadata"), "internalRefundId");
        }
        if ("charge.refunded".equals(providerEventType)) {
            UUID refundId = metadataUuid(refundObject.path("metadata"), "internalRefundId");
            return refundId == null ? metadataUuid(object.path("metadata"), "internalRefundId") : refundId;
        }
        return null;
    }

    private JsonNode refundObject(String providerEventType, JsonNode object) {
        if (!"charge.refunded".equals(providerEventType)) {
            return object;
        }
        JsonNode refunds = object.path("refunds").path("data");
        if (refunds.isArray() && !refunds.isEmpty()) {
            return refunds.get(0);
        }
        return object;
    }

    private String objectStatus(String providerEventType, JsonNode object, JsonNode refundObject) {
        if ("charge.refunded".equals(providerEventType)) {
            return firstText(text(refundObject, "status"), text(object, "status"));
        }
        return text(object, "status");
    }

    private String failureCode(
            String providerEventType,
            JsonNode object,
            JsonNode refundObject,
            String objectStatus
    ) {
        return switch (providerEventType) {
            case "payment_intent.payment_failed" -> normalizeFailureCode(
                    text(object.path("last_payment_error"), "decline_code"),
                    text(object.path("last_payment_error"), "code"),
                    "payment_failed"
            );
            case "payment_intent.canceled" -> normalizeFailureCode(
                    text(object, "cancellation_reason"),
                    "payment_canceled"
            );
            case "refund.updated", "charge.refunded" -> normalizeFailureCode(
                    text(refundObject, "failure_reason"),
                    objectStatus,
                    "refund_failed"
            );
            default -> null;
        };
    }

    private String message(ProviderWebhookEventType eventType, String objectStatus) {
        String normalizedStatus = normalizeOptional(objectStatus);
        return switch (eventType) {
            case PAYMENT_SUCCEEDED -> "Stripe payment succeeded";
            case PAYMENT_FAILED -> "Stripe payment failed: " + firstText(normalizedStatus, "failed");
            case REFUND_SUCCEEDED -> "Stripe refund succeeded";
            case REFUND_FAILED -> "Stripe refund failed: " + firstText(normalizedStatus, "failed");
            case UNSUPPORTED -> "Unsupported Stripe webhook event type";
        };
    }

    private UUID metadataUuid(JsonNode metadata, String fieldName) {
        String value = text(metadata, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeFailureCode(String... values) {
        String value = firstText(values);
        if (value == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder("stripe_");
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            } else {
                normalized.append('_');
            }
        }
        return normalized.toString().replaceAll("_+", "_");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText().isBlank() ? null : value.asText().trim();
        }
        return value.isValueNode() ? value.asText() : null;
    }

    private record StripeSignature(long timestamp, List<String> signatures) {
    }
}
