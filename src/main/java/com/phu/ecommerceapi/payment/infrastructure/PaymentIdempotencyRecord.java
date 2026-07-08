package com.phu.ecommerceapi.payment.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_idempotency_record")
public class PaymentIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long customerId;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentIdempotencyStatus status;

    private Integer responseStatus;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Column(length = 30)
    private String resourceType;

    @Column(columnDefinition = "uuid")
    private UUID resourceId;

    @Column(length = 50)
    private String providerCode;

    @Column(length = 255)
    private String providerIdempotencyKey;

    private OffsetDateTime inProgressExpiresAt;

    private OffsetDateTime lastRecoveryAttemptAt;

    @Column(length = 40)
    private String recoveryStatus;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentIdempotencyRecord() {
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public PaymentIdempotencyStatus getStatus() {
        return status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderIdempotencyKey() {
        return providerIdempotencyKey;
    }

    public OffsetDateTime getInProgressExpiresAt() {
        return inProgressExpiresAt;
    }

    public OffsetDateTime getLastRecoveryAttemptAt() {
        return lastRecoveryAttemptAt;
    }

    public String getRecoveryStatus() {
        return recoveryStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean hasRequestHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public boolean isCompleted() {
        return status == PaymentIdempotencyStatus.COMPLETED;
    }

    public void linkResource(
            String resourceType,
            UUID resourceId,
            String providerCode,
            String providerIdempotencyKey
    ) {
        if (isCompleted()) {
            return;
        }
        String normalizedResourceType = requireText(resourceType, "idempotency resource type").toUpperCase(Locale.ROOT);
        UUID normalizedResourceId = requireUuid(resourceId, "idempotency resource id");
        String normalizedProviderCode = requireText(providerCode, "idempotency provider code").toLowerCase(Locale.ROOT);
        String normalizedProviderKey = requireText(providerIdempotencyKey, "idempotency provider key");

        if (this.resourceId != null) {
            if (!Objects.equals(this.resourceType, normalizedResourceType)
                    || !Objects.equals(this.resourceId, normalizedResourceId)
                    || !Objects.equals(this.providerCode, normalizedProviderCode)
                    || !Objects.equals(this.providerIdempotencyKey, normalizedProviderKey)) {
                throw new IllegalStateException("Idempotency record is already linked to another resource");
            }
            return;
        }

        this.resourceType = normalizedResourceType;
        this.resourceId = normalizedResourceId;
        this.providerCode = normalizedProviderCode;
        this.providerIdempotencyKey = normalizedProviderKey;
        this.recoveryStatus = "NOT_REQUIRED";
    }

    public void complete(int responseStatus, String responseBody) {
        if (isCompleted()) {
            return;
        }
        if (responseStatus < 100 || responseStatus > 599) {
            throw new IllegalArgumentException("Response status must be a valid HTTP status");
        }
        if (responseBody == null) {
            throw new IllegalArgumentException("Response body is required");
        }
        this.status = PaymentIdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.completedAt = OffsetDateTime.now();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
