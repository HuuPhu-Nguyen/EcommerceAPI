package com.phu.ecommerceapi.checkout.api;

import com.phu.ecommerceapi.checkout.application.CheckoutService;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.order.application.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_CHECKOUT_CREATE)
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(checkoutService.checkout(request.cartId(), currentUser));
    }
}
