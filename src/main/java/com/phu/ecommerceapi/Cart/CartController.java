package com.phu.ecommerceapi.Cart;

import com.phu.ecommerceapi.CartItem.CartItemModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/items")
    public ResponseEntity<List<CartItemModel>> getItems(@RequestBody long cartID) {
        return ResponseEntity.ok(cartService.getCartItems(cartID));
    }

    @PostMapping("/addItem")
    public ResponseEntity<Void> addItem(@RequestBody CartDTO dto) {
        cartService.additem(dto.getCartID(), dto.getProductID(), dto.getQuantity());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/removeItem")
    public ResponseEntity<Void> removeItem(@RequestBody CartDTO dto) {
        cartService.removeitem(dto.getCartID(), dto.getProductID(), dto.getQuantity());
        return ResponseEntity.ok().build();
    }


}
