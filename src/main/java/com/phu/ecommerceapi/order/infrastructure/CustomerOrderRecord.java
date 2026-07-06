package com.phu.ecommerceapi.order.infrastructure;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class CustomerOrderRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private UserModel customer;

    @Column(nullable = false)
    private long cartId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<OrderItemRecord> items = new ArrayList<>();

    protected CustomerOrderRecord() {
    }

    private CustomerOrderRecord(UserModel customer, long cartId, String currency) {
        this.id = UUID.randomUUID();
        this.customer = Objects.requireNonNull(customer, "order customer is required");
        this.cartId = cartId;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.totalAmount = BigDecimal.ZERO.setScale(2);
        this.currency = Objects.requireNonNull(currency, "order currency is required");
        this.createdAt = OffsetDateTime.now();
    }

    public static CustomerOrderRecord pendingPayment(UserModel customer, long cartId, String currency) {
        return new CustomerOrderRecord(customer, cartId, currency);
    }

    public void addItem(ProductModel product, int quantity, BigDecimal unitPrice) {
        OrderItemRecord item = OrderItemRecord.create(this, product, quantity, unitPrice);
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotalAmount());
    }

    public UUID getId() {
        return id;
    }

    public UserModel getCustomer() {
        return customer;
    }

    public long getCartId() {
        return cartId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public List<OrderItemRecord> getItems() {
        return items;
    }
}
