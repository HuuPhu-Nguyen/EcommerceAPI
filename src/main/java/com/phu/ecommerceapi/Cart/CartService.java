package com.phu.ecommerceapi.Cart;

import com.phu.ecommerceapi.CartItem.CartItemModel;
import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {
    private final CartRepo cartRepo;
    private final ProductRepo productRepo;
    private final UserRepo userRepo;

    public CartService(CartRepo cartRepo, ProductRepo productRepo, UserRepo userRepo) {
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public void additem(long cartID, long productID, int quantity, CurrentUser currentUser) {
        CartModel cart = getOwnedCartById(cartID, currentUser);
        ProductModel product = productRepo.findById(productID)
                .orElseThrow(()-> new NotFoundException("Product not found"));

        if(product.getStock()<quantity) { throw new OutOfStockException("Not enough stock");}

        CartItemModel newItem = CartItemModel.builder()
                .productModel(product)
                .quantity(quantity)
                .cart(cart)
                .build();

        Optional<CartItemModel> cartItem = cart.getItems()
                .stream()
                .filter( item -> item.equals(newItem))
                .findFirst();

        if (cartItem.isPresent()) {
            cartItem.get().setQuantity(cartItem.get().getQuantity() + quantity);
            cart.setTotal(cart.getTotal() + quantity*cartItem.get().getProductModel().getPrice());
        }
        else{
            cart.getItems().add(newItem);
            cart.setTotal(cart.getTotal() + quantity*newItem.getProductModel().getPrice());
        }
    }

    @Transactional
    public void removeitem(long cartID, long productID, int quantity, CurrentUser currentUser) {
        CartModel cart = getOwnedCartById(cartID, currentUser);
        ProductModel product = productRepo.findById(productID)
                .orElseThrow(()-> new NotFoundException("Product not found"));

        CartItemModel newItem = CartItemModel.builder()
                .productModel(product)
                .quantity(quantity)
                .cart(cart)
                .build();

        Optional<CartItemModel> cartItem = cart.getItems()
                .stream()
                .filter( item -> item.equals(newItem))
                .findFirst();

        if (cartItem.isPresent()) {
            if (cartItem.get().getQuantity() > quantity) {
                cartItem.get().setQuantity(cartItem.get().getQuantity() - quantity);
                cart.setTotal(cart.getTotal() - quantity*cartItem.get().getProductModel().getPrice());
            }
            else{
                cart.getItems().remove(cartItem.get());
                cart.setTotal(cart.getTotal() - cartItem.get().getProductModel().getPrice()*cartItem.get().getQuantity());
            }
        }
        else{
            throw new NotFoundException("Cart item not found");
        }
    }

    public List<CartModel> getCartByUser(long userID, CurrentUser currentUser) {
        Optional<UserModel> user = userRepo.findById(userID);
        if (user.isPresent()) {
            if (!belongsToCurrentUser(user.get(), currentUser)) {
                throw new AccessDeniedException("User does not belong to current user");
            }
            return user.get().getCarts();
        }
        else{
            throw new NotFoundException("User not found");
        }
    }

    public CartModel getOwnedCartById(long id, CurrentUser currentUser) {
        CartModel cart = cartRepo.findById(id).orElseThrow(()-> new NotFoundException("Cart not found"));
        assertCartOwner(cart, currentUser);
        return cart;
    }

    public List<CartItemModel> getCartItems(long cartId, CurrentUser currentUser) {
        return getOwnedCartById(cartId, currentUser).getItems();
    }

    private void assertCartOwner(CartModel cart, CurrentUser currentUser) {
        if (currentUser == null || cart.getOwner() == null || !belongsToCurrentUser(cart.getOwner(), currentUser)) {
            throw new AccessDeniedException("Cart does not belong to current user");
        }
    }

    private boolean belongsToCurrentUser(UserModel owner, CurrentUser currentUser) {
        if (currentUser == null) {
            return false;
        }
        return matches(owner.getUsername(), currentUser.username())
                || matches(owner.getEmail(), currentUser.email());
    }

    private boolean matches(String ownerValue, String currentUserValue) {
        return ownerValue != null
                && currentUserValue != null
                && ownerValue.equalsIgnoreCase(currentUserValue);
    }

}
