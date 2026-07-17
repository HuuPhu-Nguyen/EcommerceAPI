package com.phu.ecommerceapi.cart.infrastructure;

import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.customer.infrastructure.UserModel;
import com.phu.ecommerceapi.cart.application.CartItemSnapshot;
import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;
import com.phu.ecommerceapi.customer.application.CustomerIdentity;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.domain.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "cart_model")
public class CartModel {

    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("USD");

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_model_seq")
    @SequenceGenerator(name = "cart_model_seq", sequenceName = "cart_model_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, length = 3)
    private String currency = DEFAULT_CURRENCY.getCurrencyCode();

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "cart", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<CartItemModel> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserModel owner;

    protected CartModel() {
    }

    public CartModel(UserModel owner) {
        this.owner = Objects.requireNonNull(owner, "cart owner is required");
    }

    public CartModel(CustomerIdentity owner) {
        Objects.requireNonNull(owner, "cart owner is required");
        this.owner = UserModel.reference(owner.id(), owner.identitySubject());
    }

    public long getId() {
        return id;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public Money totalMoney() {
        return Money.of(total, currency);
    }

    public long getVersion() {
        return version;
    }

    public List<CartItemModel> getItems() {
        return items;
    }

    public UserModel getOwner() {
        return owner;
    }

    public boolean belongsToIdentitySubject(String identitySubject) {
        return owner != null && Objects.equals(owner.getIdentitySubject(), identitySubject);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int quantityForProduct(long productId) {
        return findItem(productId)
                .map(CartItemModel::getQuantity)
                .orElse(0);
    }

    public void addItem(CartProductSnapshot product, int quantity) {
        if (items.isEmpty()) {
            currency = product.price().currency().getCurrencyCode();
        }
        Optional<CartItemModel> existingItem = findItem(product.id());
        if (existingItem.isPresent()) {
            CartItemModel item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            items.add(CartItemModel.create(this, productReference(product), quantity));
        }
        recalculateTotal();
    }

    public void updateItemQuantity(CartProductSnapshot product, int quantity) {
        CartItemModel item = findItem(product.id())
                .orElseThrow(() -> new NotFoundException("Cart item not found"));
        item.setQuantity(quantity);
        recalculateTotal();
    }

    public void removeItem(long productId) {
        CartItemModel item = findItem(productId)
                .orElseThrow(() -> new NotFoundException("Cart item not found"));
        items.remove(item);
        recalculateTotal();
    }

    public void clear() {
        items.clear();
        recalculateTotal();
    }

    public List<CartItemSnapshot> itemSnapshots() {
        return items.stream()
                .map(CartItemModel::snapshot)
                .toList();
    }

    private ProductModel productReference(CartProductSnapshot product) {
        return ProductModel.reference(product.id(), product.name(), product.price(), product.active());
    }

    private Optional<CartItemModel> findItem(long productId) {
        return items.stream()
                .filter(item -> item.getProductId() == productId)
                .findFirst();
    }

    private void recalculateTotal() {
        Money totalMoney = Money.zero(Currency.getInstance(currency));
        for (CartItemModel item : items) {
            totalMoney = totalMoney.add(item.lineTotalMoney());
        }
        total = totalMoney.amount();
    }
}
