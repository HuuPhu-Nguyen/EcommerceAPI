package com.phu.ecommerceapi.cart.infrastructure;

import com.phu.ecommerceapi.cart.application.CartPersistencePort;
import com.phu.ecommerceapi.cart.application.CartSnapshot;
import com.phu.ecommerceapi.cart.application.MutableCart;
import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;
import com.phu.ecommerceapi.customer.application.CustomerIdentity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

@Component
public class JpaCartPersistenceAdapter implements CartPersistencePort {

    private final CartRepo cartRepo;

    public JpaCartPersistenceAdapter(CartRepo cartRepo) {
        this.cartRepo = cartRepo;
    }

    @Override
    public CartSnapshot create(CustomerIdentity owner) {
        return toSnapshot(cartRepo.save(new CartModel(owner)));
    }

    @Override
    public Optional<CartSnapshot> findWithItemsById(long cartId) {
        return cartRepo.findWithItemsById(cartId)
                .map(this::toSnapshot);
    }

    @Override
    public <T> Optional<T> updateWithItemsForMutation(long cartId, Function<MutableCart, T> mutation) {
        return updateCart(findLockedWithItems(cartId), mutation);
    }

    @Override
    public <T> Optional<T> updateWithItemsForCheckout(long cartId, Function<MutableCart, T> mutation) {
        return updateCart(findLockedWithItems(cartId), mutation);
    }

    private Optional<CartModel> findLockedWithItems(long cartId) {
        return cartRepo.lockById(cartId)
                .flatMap(ignored -> cartRepo.findWithItemsById(cartId));
    }

    private <T> Optional<T> updateCart(Optional<CartModel> foundCart, Function<MutableCart, T> mutation) {
        return foundCart.map(cart -> {
            T result = mutation.apply(new JpaMutableCart(cart));
            cartRepo.save(cart);
            return result;
        });
    }

    private CartSnapshot toSnapshot(CartModel cart) {
        return new CartSnapshot(
                cart.getId(),
                cart.getOwner().getId(),
                cart.getOwner().getIdentitySubject(),
                cart.totalMoney(),
                cart.getCurrency(),
                cart.itemSnapshots()
        );
    }

    private final class JpaMutableCart implements MutableCart {

        private final CartModel cart;

        private JpaMutableCart(CartModel cart) {
            this.cart = cart;
        }

        @Override
        public CartSnapshot snapshot() {
            return toSnapshot(cart);
        }

        @Override
        public int quantityForProduct(long productId) {
            return cart.quantityForProduct(productId);
        }

        @Override
        public void addItem(CartProductSnapshot product, int quantity) {
            cart.addItem(product, quantity);
        }

        @Override
        public void updateItemQuantity(CartProductSnapshot product, int quantity) {
            cart.updateItemQuantity(product, quantity);
        }

        @Override
        public void removeItem(long productId) {
            cart.removeItem(productId);
        }

        @Override
        public void clear() {
            cart.clear();
        }
    }
}
