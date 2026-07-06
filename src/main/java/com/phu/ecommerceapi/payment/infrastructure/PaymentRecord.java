package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
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
@Table(name = "payment_record")
public class PaymentRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrderRecord order;

    @Column(nullable = false)
    private long customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    private String providerPaymentId;

    @Column(length = 40)
    private String providerStatus;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String providerMessage;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentRecord() {
    }

    private PaymentRecord(CustomerOrderRecord order, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.order = Objects.requireNonNull(order, "payment order is required");
        this.customerId = order.getCustomer().getId();
        this.amount = Objects.requireNonNull(order.getTotalAmount(), "payment amount is required");
        this.currency = Objects.requireNonNull(order.getCurrency(), "payment currency is required");
        this.status = PaymentStatus.PENDING;
        this.idempotencyKey = requireText(idempotencyKey, "idempotency key");
        this.createdAt = OffsetDateTime.now();
    }

    public static PaymentRecord pending(CustomerOrderRecord order, String idempotencyKey) {
        return new PaymentRecord(order, idempotencyKey);
    }

    public void markSucceeded(PaymentProviderResult providerResult) {
        if (status.isTerminal()) {
            return;
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = requireText(providerResult.providerPaymentId(), "provider payment id");
        this.providerStatus = providerResult.status().name();
        this.providerMessage = providerResult.message();
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed(PaymentProviderResult providerResult) {
        if (status.isTerminal()) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.providerPaymentId = requireText(providerResult.providerPaymentId(), "provider payment id");
        this.providerStatus = providerResult.status().name();
        this.failureCode = requireText(providerResult.failureCode(), "provider failure code");
        this.providerMessage = providerResult.message();
        this.completedAt = OffsetDateTime.now();
    }

    public void markProviderTimeout(String message) {
        if (status.isTerminal()) {
            return;
        }
        this.status = PaymentStatus.PROVIDER_TIMEOUT;
        this.providerStatus = "TIMEOUT";
        this.failureCode = "provider_timeout";
        this.providerMessage = message;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public CustomerOrderRecord getOrder() {
        return order;
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

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
