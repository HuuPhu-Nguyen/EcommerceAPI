package com.phu.ecommerceapi.payment.application;

public interface StripeWebhookEventParser {

    StripeWebhookEvent parseAndVerify(String requestBody, String signatureHeader);
}
