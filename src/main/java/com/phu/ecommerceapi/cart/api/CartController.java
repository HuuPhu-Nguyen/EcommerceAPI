package com.phu.ecommerceapi.cart.api;

import com.phu.ecommerceapi.cart.application.CartItemResponse;
import com.phu.ecommerceapi.cart.application.CartResponse;
import com.phu.ecommerceapi.cart.application.CartService;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_WRITE)
    public ResponseEntity<CartResponse> createCart(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(cartService.createCart(currentUser));
    }

    @GetMapping("/{cartId}")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_READ)
    public ResponseEntity<CartResponse> getCart(
            @PathVariable long cartId,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(cartService.viewCart(cartId, currentUser));
    }

    @GetMapping("/{cartId}/items")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_READ)
    public ResponseEntity<List<CartItemResponse>> getItems(
            @PathVariable long cartId,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(cartService.getCartItems(cartId, currentUser));
    }

    @PostMapping("/{cartId}/items")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_WRITE)
    public ResponseEntity<CartResponse> addItem(
            @PathVariable long cartId,
            @Valid @RequestBody AddCartItemRequest request,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(cartService.addItem(
                cartId,
                request.productId(),
                request.quantity(),
                currentUser
        ));
    }

    @PutMapping("/{cartId}/items/{productId}")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_WRITE)
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable long cartId,
            @PathVariable long productId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(cartService.updateItemQuantity(
                cartId,
                productId,
                request.quantity(),
                currentUser
        ));
    }

    @DeleteMapping("/{cartId}/items/{productId}")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_WRITE)
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable long cartId,
            @PathVariable long productId,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(cartService.removeItem(cartId, productId, currentUser));
    }
}
