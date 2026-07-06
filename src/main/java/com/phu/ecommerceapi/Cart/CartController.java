package com.phu.ecommerceapi.Cart;

import com.phu.ecommerceapi.CartItem.CartItemModel;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/{cartId}/items")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_READ)
    public ResponseEntity<List<CartItemModel>> getItems(
            @PathVariable long cartId,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(cartService.getCartItems(cartId, currentUser));
    }

    @PostMapping("/addItem")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_WRITE)
    public ResponseEntity<Void> addItem(
            @RequestBody CartDTO dto,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        cartService.additem(dto.getCartID(), dto.getProductID(), dto.getQuantity(), currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/removeItem")
    @PreAuthorize(SecurityExpressions.CUSTOMER_CART_WRITE)
    public ResponseEntity<Void> removeItem(
            @RequestBody CartDTO dto,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        cartService.removeitem(dto.getCartID(), dto.getProductID(), dto.getQuantity(), currentUser);
        return ResponseEntity.ok().build();
    }


}
