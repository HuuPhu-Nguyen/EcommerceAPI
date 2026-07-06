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
}
