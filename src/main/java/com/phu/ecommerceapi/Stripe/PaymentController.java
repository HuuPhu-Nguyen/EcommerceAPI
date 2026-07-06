package com.phu.ecommerceapi.Stripe;

import com.phu.ecommerceapi.Cart.CartModel;
import com.phu.ecommerceapi.Cart.CartService;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.stripe.model.PaymentIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private CartService cartService;

    @PostMapping("/{cartId}")
    @PreAuthorize(SecurityExpressions.CUSTOMER_PAYMENT_CREATE)
    public ResponseEntity<Map<String, String>> createPaymentIntent(
            @PathVariable long cartId,
            @AuthenticatedUser CurrentUser currentUser
    ) throws Exception {
        try {
            CartModel cart = cartService.getOwnedCartById(cartId, currentUser);

            long amount = (long) (cart.getTotal() * 100); // Stripe uses cents

            Map<String, Object> params = new HashMap<>();
            params.put("amount", amount);
            params.put("currency", "usd");
            params.put("automatic_payment_methods", Map.of("enabled", true));

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            return ResponseEntity.ok(Map.of("clientSecret", paymentIntent.getClientSecret()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
