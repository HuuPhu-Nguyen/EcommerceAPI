package com.phu.ecommerceapi.Product;

import com.phu.ecommerceapi.cart.infrastructure.CartItemModel;
import com.phu.ecommerceapi.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    private int stock;

    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "productModel",cascade = CascadeType.ALL)
    private List<CartItemModel> cartItems;

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
