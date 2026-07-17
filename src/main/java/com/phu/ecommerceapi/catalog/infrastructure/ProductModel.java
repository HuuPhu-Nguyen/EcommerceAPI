package com.phu.ecommerceapi.catalog.infrastructure;

import com.phu.ecommerceapi.cart.infrastructure.CartItemModel;
import com.phu.ecommerceapi.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProductModel {

    @EqualsAndHashCode.Include
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long productId;

    @EqualsAndHashCode.Include
    private String name;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "productModel")
    private List<CartItemModel> cartItems;

    public static ProductModel reference(long productId, String name, Money price, boolean active) {
        ProductModel product = new ProductModel();
        product.productId = productId;
        product.name = name;
        product.setPrice(price);
        product.active = active;
        return product;
    }

    public long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Money priceMoney() {
        return Money.of(price == null ? BigDecimal.ZERO : price, currencyOrDefault());
    }

    public void setPrice(Money price) {
        Money requiredPrice = java.util.Objects.requireNonNull(price, "product price is required");
        this.price = requiredPrice.amount();
        this.currency = requiredPrice.currency().getCurrencyCode();
    }

    public void setPrice(BigDecimal price) {
        setPrice(Money.of(price, currencyOrDefault()));
    }

    public void setCurrency(String currency) {
        this.currency = normalizeCurrency(currency);
        this.price = Money.of(price == null ? BigDecimal.ZERO : price, this.currency).amount();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    @PreUpdate
    void normalizeMoneyFields() {
        Money normalizedPrice = priceMoney();
        this.price = normalizedPrice.amount();
        this.currency = normalizedPrice.currency().getCurrencyCode();
    }

    private String currencyOrDefault() {
        return normalizeCurrency(currency);
    }

    private String normalizeCurrency(String currency) {
        String candidate = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
        return Currency.getInstance(candidate).getCurrencyCode();
    }
}
