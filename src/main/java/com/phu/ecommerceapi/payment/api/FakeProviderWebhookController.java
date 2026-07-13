package com.phu.ecommerceapi.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.config.FakeProviderEnabledCondition;
import com.phu.ecommerceapi.payment.application.FakeProviderWebhookUseCase;
import com.phu.ecommerceapi.payment.application.ProviderWebhookCommand;
import com.phu.ecommerceapi.payment.application.ProviderWebhookResult;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Conditional(FakeProviderEnabledCondition.class)
@RequestMapping("/payments/provider-webhooks/fake")
public class FakeProviderWebhookController {

    private static final String PROVIDER_CODE = "fake";

    public static final String WEBHOOK_SECRET_HEADER = "X-Fake-Provider-Webhook-Secret";

    private final FakeProviderWebhookUseCase fakeProviderWebhookUseCase;
    private final ObjectMapper objectMapper;

    public FakeProviderWebhookController(
            FakeProviderWebhookUseCase fakeProviderWebhookUseCase,
            ObjectMapper objectMapper
    ) {
        this.fakeProviderWebhookUseCase = fakeProviderWebhookUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ProviderWebhookResponse> handleWebhook(
            @RequestHeader(name = WEBHOOK_SECRET_HEADER, required = false) String webhookSecret,
            @RequestBody(required = false) String requestBody
    ) {
        FakeProviderWebhookRequest request = parseRequest(requestBody);
        ProviderWebhookResult result = fakeProviderWebhookUseCase.handle(new ProviderWebhookCommand(
                PROVIDER_CODE,
                webhookSecret,
                requestBody,
                request.eventId(),
                request.eventType(),
                request.paymentId(),
                request.refundId(),
                request.providerPaymentId(),
                request.providerRefundId(),
                request.failureCode(),
                request.message()
        ));
        return ResponseEntity
                .status(result.httpStatus())
                .body(ProviderWebhookResponse.from(result.response()));
    }

    private FakeProviderWebhookRequest parseRequest(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("provider webhook request body is required");
        }
        try {
            return objectMapper.readValue(requestBody, FakeProviderWebhookRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("provider webhook request body is invalid", exception);
        }
    }
}
