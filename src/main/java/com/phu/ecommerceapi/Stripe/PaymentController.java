package com.phu.ecommerceapi.Stripe;

import com.phu.ecommerceapi.Cart.CartModel;
import com.phu.ecommerceapi.Cart.CartService;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private CartService cartService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createPaymentIntent(@PathVariable long cartId) throws Exception {
        try {
            CartModel cart = cartService.getCartById(cartId);

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
