package com.phu.ecommerceapi.payment.api;

import com.phu.ecommerceapi.payment.application.ProviderWebhookResult;
import com.phu.ecommerceapi.payment.application.StripeProviderWebhookUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments/provider-webhooks/stripe")
public class StripeProviderWebhookController {

    public static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final StripeProviderWebhookUseCase stripeProviderWebhookUseCase;

    public StripeProviderWebhookController(StripeProviderWebhookUseCase stripeProviderWebhookUseCase) {
        this.stripeProviderWebhookUseCase = stripeProviderWebhookUseCase;
    }

    @PostMapping
    public ResponseEntity<ProviderWebhookResponse> handleWebhook(
            @RequestHeader(name = STRIPE_SIGNATURE_HEADER, required = false) String signatureHeader,
            @RequestBody(required = false) String requestBody
    ) {
        ProviderWebhookResult result = stripeProviderWebhookUseCase.handle(signatureHeader, requestBody);
        return ResponseEntity
                .status(result.httpStatus())
                .body(ProviderWebhookResponse.from(result.response()));
    }
}
