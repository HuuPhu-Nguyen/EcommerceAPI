package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentRefundProviderResult;
import com.phu.ecommerceapi.payment.domain.RefundStateMachine;
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
import java.util.Locale;
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

    @Column(nullable = false, length = 50)
    private String providerCode;

    @Column(nullable = false, length = 255)
    private String providerIdempotencyKey;

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

    private RefundRecord(
            PaymentRecord payment,
            String idempotencyKey,
            String providerIdempotencyKey,
            String reason
    ) {
        this.id = UUID.randomUUID();
        this.payment = Objects.requireNonNull(payment, "refund payment is required");
        this.orderId = payment.getOrder().getId();
        this.customerId = payment.getCustomerId();
        this.amount = Objects.requireNonNull(payment.getAmount(), "refund amount is required");
        this.currency = Objects.requireNonNull(payment.getCurrency(), "refund currency is required");
        this.status = RefundStatus.PENDING;
        this.providerCode = normalizeProviderCode(payment.getProviderCode());
        this.providerIdempotencyKey = requireText(providerIdempotencyKey, "provider idempotency key");
        this.idempotencyKey = requireText(idempotencyKey, "idempotency key");
        this.reason = normalizeReason(reason);
        this.createdAt = OffsetDateTime.now();
    }

    public static RefundRecord pending(
            PaymentRecord payment,
            String idempotencyKey,
            String providerIdempotencyKey,
            String reason
    ) {
        return new RefundRecord(payment, idempotencyKey, providerIdempotencyKey, reason);
    }

    public void markSucceeded(PaymentRefundProviderResult providerResult) {
        RefundStatus nextStatus = RefundStateMachine.providerSucceeded(status);
        if (nextStatus == status) {
            return;
        }
        this.status = nextStatus;
        this.providerRefundId = requireText(providerResult.providerRefundId(), "provider refund id");
        this.providerStatus = providerResult.status().name();
        this.providerMessage = providerResult.message();
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed(PaymentRefundProviderResult providerResult) {
        RefundStatus nextStatus = RefundStateMachine.providerFailed(status);
        if (nextStatus == status) {
            return;
        }
        this.status = nextStatus;
        this.providerRefundId = requireText(providerResult.providerRefundId(), "provider refund id");
        this.providerStatus = providerResult.status().name();
        this.failureCode = requireText(providerResult.failureCode(), "provider refund failure code");
        this.providerMessage = providerResult.message();
        this.completedAt = OffsetDateTime.now();
    }

    public boolean markPending(PaymentRefundProviderResult providerResult) {
        if (status.isTerminal()) {
            return false;
        }
        this.providerRefundId = requireText(providerResult.providerRefundId(), "provider refund id");
        this.providerStatus = providerResult.status().name();
        this.providerMessage = providerResult.message();
        return true;
    }

    public void markProviderTimeout(String message) {
        RefundStatus nextStatus = RefundStateMachine.providerTimedOut(status);
        if (nextStatus == status) {
            return;
        }
        this.status = nextStatus;
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

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderIdempotencyKey() {
        return providerIdempotencyKey;
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

    private String normalizeProviderCode(String value) {
        return requireText(value, "provider code").toLowerCase(Locale.ROOT);
    }
}
