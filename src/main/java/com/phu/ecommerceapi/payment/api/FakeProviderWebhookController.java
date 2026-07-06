package com.phu.ecommerceapi.payment.api;

import com.phu.ecommerceapi.payment.application.FakeProviderWebhookResult;
import com.phu.ecommerceapi.payment.application.FakeProviderWebhookUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments/provider-webhooks/fake")
public class FakeProviderWebhookController {

    public static final String WEBHOOK_SECRET_HEADER = "X-Fake-Provider-Webhook-Secret";

    private final FakeProviderWebhookUseCase fakeProviderWebhookUseCase;

    public FakeProviderWebhookController(FakeProviderWebhookUseCase fakeProviderWebhookUseCase) {
        this.fakeProviderWebhookUseCase = fakeProviderWebhookUseCase;
    }

    @PostMapping
    public ResponseEntity<ProviderWebhookResponse> handleWebhook(
            @RequestHeader(name = WEBHOOK_SECRET_HEADER, required = false) String webhookSecret,
            @RequestBody(required = false) String requestBody
    ) {
        FakeProviderWebhookResult result = fakeProviderWebhookUseCase.handle(webhookSecret, requestBody);
        return ResponseEntity
                .status(result.httpStatus())
                .body(result.response());
    }
}
