package com.phu.ecommerceapi.Cart;

import com.phu.ecommerceapi.CartItem.CartItemModel;
import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {
    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private UserRepo userRepo;

    @Transactional
    public void additem(long cartID, long productID, int quantity ) {
        CartModel cart = cartRepo.findById(cartID)
                .orElseThrow(() -> new NotFoundException("Cart not found"));
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
    public void removeitem(long cartID, long productID, int quantity) {
        CartModel cart = cartRepo.findById(cartID)
                .orElseThrow(() -> new NotFoundException("Cart not found"));
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

    public List<CartModel> getCartByUser(long userID) {
        Optional<UserModel> user = userRepo.findById(userID);
        if (user.isPresent()) {
            return user.get().getCarts();
        }
        else{
            throw new NotFoundException("User not found");
        }
    }

    public CartModel getCartById(long id) {
        return cartRepo.findById(id).orElseThrow(()-> new NotFoundException("Cart not found"));
    }

    public List<CartItemModel> getCartItems(long cartId) {
        CartModel cart =  cartRepo.findById(cartId).orElseThrow(()-> new NotFoundException("Cart not found"));
        return cart.getItems();
    }

}
