package com.phu.ecommerceapi.order.infrastructure;

import com.phu.ecommerceapi.Product.ProductModel;
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

    private OrderItemRecord(CustomerOrderRecord order, ProductModel product, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order item quantity must be positive");
        }
        this.order = Objects.requireNonNull(order, "order is required");
        this.product = Objects.requireNonNull(product, "product is required");
        this.productName = Objects.requireNonNull(product.getName(), "product name is required");
        this.quantity = quantity;
        this.unitPriceAmount = Objects.requireNonNull(unitPrice, "unit price is required");
        this.lineTotalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static OrderItemRecord create(
            CustomerOrderRecord order,
            ProductModel product,
            int quantity,
            BigDecimal unitPrice
    ) {
        return new OrderItemRecord(order, product, quantity, unitPrice);
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
