package com.phu.ecommerceapi.cart.infrastructure;

import com.phu.ecommerceapi.Product.ProductModel;
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

@Entity
@Table(name = "cart_item_model")
public class CartItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductModel productModel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartModel cart;

    @Column(nullable = false)
    private int quantity;

    protected CartItemModel() {
    }

    private CartItemModel(CartModel cart, ProductModel productModel, int quantity) {
        this.cart = cart;
        this.productModel = productModel;
        setQuantity(quantity);
    }

    public static CartItemModel create(CartModel cart, ProductModel productModel, int quantity) {
        return new CartItemModel(cart, productModel, quantity);
    }

    public long getId() {
        return id;
    }

    public ProductModel getProductModel() {
        return productModel;
    }

    public CartModel getCart() {
        return cart;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Cart item quantity must be positive");
        }
        this.quantity = quantity;
    }

    public long getProductId() {
        return productModel.getProductId();
    }

    public Money lineTotalMoney() {
        return productModel.priceMoney().multiply(Quantity.of(quantity));
    }
}
