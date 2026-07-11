package com.phu.ecommerceapi.payment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.StripeWebhookEvent;
import com.phu.ecommerceapi.payment.application.StripeWebhookSignatureException;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeWebhookEventParserAdapterTest {

    private static final String WEBHOOK_SECRET = "whsec_parser_test";
    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

    private final StripeWebhookEventParserAdapter parser = new StripeWebhookEventParserAdapter(
            new ObjectMapper(),
            appProperties(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void validSignatureParsesPaymentIntentSuccess() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {
                  "id": "evt_payment_success",
                  "created": 1783763990,
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "pi_success",
                      "object": "payment_intent",
                      "status": "succeeded",
                      "metadata": {
                        "internalPaymentId": "%s"
                      }
                    }
                  }
                }
                """.formatted(paymentId);

        StripeWebhookEvent event = parser.parseAndVerify(payload, signature(payload));

        assertThat(event.eventId()).isEqualTo("evt_payment_success");
        assertThat(event.eventType()).isEqualTo(ProviderWebhookEventType.PAYMENT_SUCCEEDED);
        assertThat(event.providerEventType()).isEqualTo("payment_intent.succeeded");
        assertThat(event.providerObjectId()).isEqualTo("pi_success");
        assertThat(event.providerObjectType()).isEqualTo("payment_intent");
        assertThat(event.providerPaymentId()).isEqualTo("pi_success");
        assertThat(event.paymentId()).isEqualTo(paymentId);
        assertThat(event.createdAt()).isEqualTo(Instant.ofEpochSecond(1783763990).atOffset(ZoneOffset.UTC));
    }

    @Test
    void invalidSignatureIsRejectedBeforeParsing() {
        String payload = """
                {
                  "id": "evt_invalid_signature",
                  "type": "payment_intent.succeeded"
                }
                """;

        assertThatThrownBy(() -> parser.parseAndVerify(payload, "t=%d,v1=bad".formatted(NOW.getEpochSecond())))
                .isInstanceOf(StripeWebhookSignatureException.class)
                .hasMessage("Stripe webhook signature is invalid");
    }

    @Test
    void chargeRefundedUsesNestedRefundReference() {
        UUID refundId = UUID.randomUUID();
        String payload = """
                {
                  "id": "evt_charge_refunded",
                  "created": 1783763990,
                  "type": "charge.refunded",
                  "data": {
                    "object": {
                      "id": "ch_refunded",
                      "object": "charge",
                      "payment_intent": "pi_refunded",
                      "refunds": {
                        "data": [
                          {
                            "id": "re_succeeded",
                            "object": "refund",
                            "status": "succeeded",
                            "metadata": {
                              "internalRefundId": "%s"
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(refundId);

        StripeWebhookEvent event = parser.parseAndVerify(payload, signature(payload));

        assertThat(event.eventType()).isEqualTo(ProviderWebhookEventType.REFUND_SUCCEEDED);
        assertThat(event.providerObjectId()).isEqualTo("ch_refunded");
        assertThat(event.providerObjectType()).isEqualTo("charge");
        assertThat(event.providerPaymentId()).isEqualTo("pi_refunded");
        assertThat(event.providerRefundId()).isEqualTo("re_succeeded");
        assertThat(event.refundId()).isEqualTo(refundId);
    }

    private String signature(String payload) {
        long timestamp = NOW.getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        return "t=%d,v1=%s".formatted(timestamp, hmacHex(signedPayload));
    }

    private String hmacHex(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AppProperties appProperties() {
        return new AppProperties(
                "test",
                "keycloak",
                new AppProperties.PaymentProviderProperties("fake", List.of("fake")),
                new AppProperties.FakeProvider("fake-webhook-secret"),
                new AppProperties.StripeProviderProperties(
                        "sk_test_safe_placeholder",
                        WEBHOOK_SECRET,
                        "",
                        2000,
                        5000
                )
        );
    }
}
