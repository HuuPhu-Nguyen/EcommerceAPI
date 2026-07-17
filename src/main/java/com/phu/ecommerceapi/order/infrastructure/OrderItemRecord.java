package com.phu.ecommerceapi.order.infrastructure;

import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.shared.domain.Money;
import com.phu.ecommerceapi.shared.domain.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "customer_order_item")
public class OrderItemRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrderRecord order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductModel product;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPriceAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotalAmount;

    protected OrderItemRecord() {
    }

    private OrderItemRecord(CustomerOrderRecord order, ProductModel product, int quantity, Money unitPrice) {
        this(order, product.getProductId(), product.getName(), quantity, unitPrice);
    }

    private OrderItemRecord(
            CustomerOrderRecord order,
            long productId,
            String productName,
            int quantity,
            Money unitPrice
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order item quantity must be positive");
        }
        this.order = Objects.requireNonNull(order, "order is required");
        this.productName = Objects.requireNonNull(productName, "product name is required");
        this.quantity = quantity;
        Money requiredUnitPrice = Objects.requireNonNull(unitPrice, "unit price is required");
        this.product = ProductModel.reference(productId, productName, requiredUnitPrice, true);
        this.unitPriceAmount = requiredUnitPrice.amount();
        this.lineTotalAmount = requiredUnitPrice.multiply(Quantity.of(quantity)).amount();
    }

    public static OrderItemRecord create(
            CustomerOrderRecord order,
            ProductModel product,
            int quantity,
            Money unitPrice
    ) {
        return new OrderItemRecord(order, product, quantity, unitPrice);
    }

    public static OrderItemRecord create(
            CustomerOrderRecord order,
            long productId,
            String productName,
            int quantity,
            Money unitPrice
    ) {
        return new OrderItemRecord(order, productId, productName, quantity, unitPrice);
    }

    public long getId() {
        return id;
    }

    public CustomerOrderRecord getOrder() {
        return order;
    }

    public long getProductId() {
        return product.getProductId();
    }

    public ProductModel getProduct() {
        return product;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceAmount() {
        return unitPriceAmount;
    }

    public BigDecimal getLineTotalAmount() {
        return lineTotalAmount;
    }
}
