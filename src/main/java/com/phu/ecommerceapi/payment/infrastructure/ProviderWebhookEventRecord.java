package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "provider_webhook_event")
public class ProviderWebhookEventRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String providerName;

    @Column(nullable = false)
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private ProviderWebhookEventType eventType;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProviderWebhookProcessingStatus processingStatus;

    @Column(length = 500)
    private String processingMessage;

    @Column(nullable = false)
    private OffsetDateTime receivedAt;

    private OffsetDateTime processedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProviderWebhookEventRecord() {
    }

    private ProviderWebhookEventRecord(
            String providerName,
            String providerEventId,
            ProviderWebhookEventType eventType,
        String payloadHash,
        String payload
    ) {
        this.id = UUID.randomUUID();
        this.providerName = requireText(providerName, "provider name").toLowerCase(Locale.ROOT);
        this.providerEventId = requireText(providerEventId, "provider event id");
        this.eventType = Objects.requireNonNull(eventType, "provider event type is required");
        this.payloadHash = requireText(payloadHash, "provider event payload hash");
        this.payload = requireText(payload, "provider event payload");
        this.processingStatus = ProviderWebhookProcessingStatus.RECEIVED;
        this.receivedAt = OffsetDateTime.now();
    }

    public static ProviderWebhookEventRecord received(
            String providerName,
            String providerEventId,
            ProviderWebhookEventType eventType,
            String payloadHash,
            String payload
    ) {
        return new ProviderWebhookEventRecord(providerName, providerEventId, eventType, payloadHash, payload);
    }

    public boolean hasPayloadHash(String candidateHash) {
        return payloadHash.equals(candidateHash);
    }

    public void markProcessed(String message) {
        markCompleted(ProviderWebhookProcessingStatus.PROCESSED, message);
    }

    public void markIgnored(String message) {
        markCompleted(ProviderWebhookProcessingStatus.IGNORED, message);
    }

    public void markRejected(String message) {
        markCompleted(ProviderWebhookProcessingStatus.REJECTED, message);
    }

    public UUID getId() {
        return id;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public ProviderWebhookEventType getEventType() {
        return eventType;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getPayload() {
        return payload;
    }

    public ProviderWebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public String getProcessingMessage() {
        return processingMessage;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public long getVersion() {
        return version;
    }

    private void markCompleted(ProviderWebhookProcessingStatus status, String message) {
        this.processingStatus = Objects.requireNonNull(status, "provider webhook status is required");
        this.processingMessage = message;
        this.processedAt = OffsetDateTime.now();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
