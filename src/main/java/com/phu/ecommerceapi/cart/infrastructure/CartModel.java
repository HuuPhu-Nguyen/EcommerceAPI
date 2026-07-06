package com.phu.ecommerceapi.cart.infrastructure;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.shared.api.NotFoundException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "cart_model")
public class CartModel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_model_seq")
    @SequenceGenerator(name = "cart_model_seq", sequenceName = "cart_model_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false)
    private double total;

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

    public long getId() {
        return id;
    }

    public double getTotal() {
        return total;
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

    public int quantityForProduct(long productId) {
        return findItem(productId)
                .map(CartItemModel::getQuantity)
                .orElse(0);
    }

    public void addItem(ProductModel product, int quantity) {
        Optional<CartItemModel> existingItem = findItem(product.getProductId());
        if (existingItem.isPresent()) {
            CartItemModel item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            items.add(CartItemModel.create(this, product, quantity));
        }
        recalculateTotal();
    }

    public void updateItemQuantity(ProductModel product, int quantity) {
        CartItemModel item = findItem(product.getProductId())
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

    private Optional<CartItemModel> findItem(long productId) {
        return items.stream()
                .filter(item -> item.getProductId() == productId)
                .findFirst();
    }

    private void recalculateTotal() {
        total = items.stream()
                .mapToDouble(CartItemModel::lineTotal)
                .sum();
    }
}
