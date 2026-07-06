package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentRefundProviderResult;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refund_record")
public class RefundRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentRecord payment;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(nullable = false)
    private long customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundStatus status;

    private String providerRefundId;

    @Column(length = 40)
    private String providerStatus;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String providerMessage;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RefundRecord() {
    }

    private RefundRecord(PaymentRecord payment, String idempotencyKey, String reason) {
        this.id = UUID.randomUUID();
        this.payment = Objects.requireNonNull(payment, "refund payment is required");
        this.orderId = payment.getOrder().getId();
        this.customerId = payment.getCustomerId();
        this.amount = Objects.requireNonNull(payment.getAmount(), "refund amount is required");
        this.currency = Objects.requireNonNull(payment.getCurrency(), "refund currency is required");
        this.status = RefundStatus.PENDING;
        this.idempotencyKey = requireText(idempotencyKey, "idempotency key");
        this.reason = normalizeReason(reason);
        this.createdAt = OffsetDateTime.now();
    }

    public static RefundRecord pending(PaymentRecord payment, String idempotencyKey, String reason) {
        return new RefundRecord(payment, idempotencyKey, reason);
    }

    public void markSucceeded(PaymentRefundProviderResult providerResult) {
        if (status.isTerminal()) {
            return;
        }
        this.status = RefundStatus.SUCCEEDED;
        this.providerRefundId = requireText(providerResult.providerRefundId(), "provider refund id");
        this.providerStatus = providerResult.status().name();
        this.providerMessage = providerResult.message();
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed(PaymentRefundProviderResult providerResult) {
        if (status.isTerminal()) {
            return;
        }
        this.status = RefundStatus.FAILED;
        this.providerRefundId = requireText(providerResult.providerRefundId(), "provider refund id");
        this.providerStatus = providerResult.status().name();
        this.failureCode = requireText(providerResult.failureCode(), "provider refund failure code");
        this.providerMessage = providerResult.message();
        this.completedAt = OffsetDateTime.now();
    }

    public void markProviderTimeout(String message) {
        if (status.isTerminal()) {
            return;
        }
        this.status = RefundStatus.PROVIDER_TIMEOUT;
        this.providerStatus = "TIMEOUT";
        this.failureCode = "provider_timeout";
        this.providerMessage = message;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public PaymentRecord getPayment() {
        return payment;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getProviderMessage() {
        return providerMessage;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReason() {
        return reason;
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

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return "customer_request";
        }
        return value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
