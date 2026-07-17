package com.phu.ecommerceapi.payment.api;

import com.jayway.jsonpath.JsonPath;
import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.catalog.infrastructure.ProductRepo;
import com.phu.ecommerceapi.customer.infrastructure.UserModel;
import com.phu.ecommerceapi.customer.infrastructure.UserRepo;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRecord;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.application.PaymentAttemptService;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.StripePaymentIntentSnapshot;
import com.phu.ecommerceapi.payment.application.StripeProviderReadPort;
import com.phu.ecommerceapi.payment.application.StripeRefundSnapshot;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.ProviderWebhookEventRecord;
import com.phu.ecommerceapi.payment.infrastructure.ProviderWebhookEventRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecord;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.phu.ecommerceapi.audit.AuditEventTestCleaner.clearAuditEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.stripe.webhook-secret=whsec_test_webhook")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderWebhookHandlingTest {

    private static final String WEBHOOK_SECRET = "local-fake-webhook-secret";
    private static final String STRIPE_WEBHOOK_SECRET = "whsec_test_webhook";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private PaymentRecordRepository paymentRepository;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private RefundRecordRepository refundRepository;

    @Autowired
    private ProviderWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentIdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StripeProviderReadPort stripeProviderReadPort;

    @BeforeEach
    void resetData() {
        reset(stripeProviderReadPort);
        truncateLedger();
        webhookEventRepository.deleteAll();
        clearAuditEvents(jdbcTemplate);
        idempotencyRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
        userRepo.deleteAll();
    }

    @AfterEach
    void cleanUpData() {
        resetData();
    }

    @Test
    void paymentSucceededWebhookCompletesPendingPaymentAndPostsLedger() throws Exception {
        String username = "webhook-payment@example.com";
        PaymentFixture fixture = pendingPayment(username);
        String providerPaymentId = "fake_webhook_payment_" + UUID.randomUUID();

        sendWebhook(paymentWebhookJson(
                        "evt-payment-success-1",
                        "payment.succeeded",
                        fixture.paymentId(),
                        providerPaymentId,
                        null,
                        "payment completed by webhook"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerEventId").value("evt-payment-success-1"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        ProviderWebhookEventRecord event = webhookEventRepository.findAll().get(0);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo(providerPaymentId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(event.getProviderName()).isEqualTo("fake");
        assertThat(event.getProcessingStatus()).isEqualTo(ProviderWebhookProcessingStatus.PROCESSED);
        assertThat(auditActions()).contains("PAYMENT_SUCCEEDED", "PROVIDER_WEBHOOK_PROCESSED");
        assertWebhookAuditDetails(
                "PROVIDER_WEBHOOK_PROCESSED",
                "provider=fake",
                "eventId=evt-payment-success-1",
                "eventType=PAYMENT_SUCCEEDED",
                "status=PROCESSED"
        );
    }

    @Test
    void duplicateWebhookEventIsSafeNoop() throws Exception {
        String username = "webhook-duplicate@example.com";
        PaymentFixture fixture = pendingPayment(username);
        String requestBody = paymentWebhookJson(
                "evt-payment-duplicate-1",
                "payment.succeeded",
                fixture.paymentId(),
                "fake_webhook_payment_duplicate",
                null,
                "payment completed by webhook"
        );

        sendWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
        sendWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("PROVIDER_WEBHOOK_PROCESSED"::equals)).hasSize(1);
    }

    @Test
    void concurrentFakeSameEventDeliveryProcessesOnce() throws Exception {
        String username = "webhook-fake-concurrent-same@example.com";
        PaymentFixture fixture = pendingPayment(username);
        String requestBody = paymentWebhookJson(
                "evt-fake-concurrent-same",
                "payment.succeeded",
                fixture.paymentId(),
                "fake_webhook_payment_concurrent_same",
                null,
                "concurrent fake duplicate"
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> delivery = () -> {
                start.await(5, TimeUnit.SECONDS);
                return sendWebhook(requestBody)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> first = executor.submit(delivery);
            Future<Integer> second = executor.submit(delivery);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsOnly(200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("PROVIDER_WEBHOOK_PROCESSED"::equals)).hasSize(1);
    }

    @Test
    void concurrentFakeDifferentEventsForSamePaymentDoNotDuplicateLedgerEntries() throws Exception {
        String username = "webhook-fake-concurrent-different@example.com";
        PaymentFixture fixture = pendingPayment(username);
        String providerPaymentId = "fake_webhook_payment_concurrent_different";
        String firstRequest = paymentWebhookJson(
                "evt-fake-concurrent-different-1",
                "payment.succeeded",
                fixture.paymentId(),
                providerPaymentId,
                null,
                "first concurrent fake success"
        );
        String secondRequest = paymentWebhookJson(
                "evt-fake-concurrent-different-2",
                "payment.succeeded",
                fixture.paymentId(),
                providerPaymentId,
                null,
                "second concurrent fake success"
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> firstDelivery = () -> {
                start.await(5, TimeUnit.SECONDS);
                return sendWebhook(firstRequest)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Callable<Integer> secondDelivery = () -> {
                start.await(5, TimeUnit.SECONDS);
                return sendWebhook(secondRequest)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> first = executor.submit(firstDelivery);
            Future<Integer> second = executor.submit(secondDelivery);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsOnly(200);
        } finally {
            executor.shutdownNow();
        }

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(webhookEventRepository.findAll())
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.getProcessingStatus())
                        .isIn(
                                ProviderWebhookProcessingStatus.PROCESSED,
                                ProviderWebhookProcessingStatus.IGNORED
                        ));
        assertThat(auditActions().stream().filter("PAYMENT_SUCCEEDED"::equals)).hasSize(1);
    }

    @Test
    void sameEventIdWithDifferentPayloadReturnsConflictAndAudits() throws Exception {
        String username = "webhook-conflict@example.com";
        PaymentFixture fixture = pendingPayment(username);

        sendWebhook(paymentWebhookJson(
                        "evt-payment-conflict-1",
                        "payment.succeeded",
                        fixture.paymentId(),
                        "fake_webhook_payment_conflict",
                        null,
                        "first payload"
                ))
                .andExpect(status().isOk());

        sendWebhook(paymentWebhookJson(
                        "evt-payment-conflict-1",
                        "payment.succeeded",
                        fixture.paymentId(),
                        "fake_webhook_payment_conflict",
                        null,
                        "changed payload"
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Provider event id was reused with a different payload"));

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_PAYLOAD_CONFLICT");
    }

    @Test
    void outOfOrderFailedWebhookDoesNotCorruptSucceededPayment() throws Exception {
        String username = "webhook-out-of-order@example.com";
        PaymentFixture fixture = successfulPayment(username);

        sendWebhook(paymentWebhookJson(
                        "evt-payment-failed-after-success",
                        "payment.failed",
                        fixture.paymentId(),
                        fixture.providerPaymentId(),
                        "late_failure",
                        "late failed event"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_IGNORED");
        assertThat(auditActions()).doesNotContain("PAYMENT_FAILED");
    }

    @Test
    void successAfterFailedFakeWebhookIsIgnored() throws Exception {
        String username = "webhook-success-after-failed@example.com";
        PaymentFixture fixture = pendingPayment(username);
        String providerPaymentId = "fake_webhook_payment_failed_before_success";
        paymentAttemptService.completeAttempt(
                fixture.paymentId(),
                PaymentProviderResult.failed(providerPaymentId, "declined", "seeded failure before webhook success"),
                null
        );

        sendWebhook(paymentWebhookJson(
                        "evt-payment-success-after-failed",
                        "payment.succeeded",
                        fixture.paymentId(),
                        providerPaymentId,
                        null,
                        "late success after local failure"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentCaptureTransactions()).isEmpty();
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_IGNORED");
        assertThat(auditActions()).doesNotContain("PAYMENT_SUCCEEDED");
    }

    @Test
    void unknownPaymentWebhookIsRejectedAndAudited() throws Exception {
        sendWebhook(paymentWebhookJson(
                        "evt-payment-unknown-1",
                        "payment.succeeded",
                        UUID.randomUUID(),
                        "fake_unknown_payment",
                        null,
                        "unknown payment"
                ))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Payment not found for provider webhook event"));

        ProviderWebhookEventRecord event = webhookEventRepository.findAll().get(0);
        assertThat(event.getProcessingStatus()).isEqualTo(ProviderWebhookProcessingStatus.REJECTED);
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_REJECTED");
    }

    @Test
    void fakeWebhookCannotUpdatePaymentStoredForDifferentProvider() throws Exception {
        String username = "webhook-provider-mismatch@example.com";
        PaymentFixture fixture = pendingPayment(username, "stripe");

        sendWebhook(paymentWebhookJson(
                        "evt-provider-mismatch-1",
                        "payment.succeeded",
                        fixture.paymentId(),
                        "fake_provider_mismatch_payment",
                        null,
                        "wrong provider event"
                ))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Payment not found for provider webhook event"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        ProviderWebhookEventRecord event = webhookEventRepository.findAll().get(0);

        assertThat(payment.getProviderCode()).isEqualTo("stripe");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProviderPaymentId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentCaptureTransactions()).isEmpty();
        assertThat(event.getProviderName()).isEqualTo("fake");
        assertThat(event.getProcessingStatus()).isEqualTo(ProviderWebhookProcessingStatus.REJECTED);
        assertWebhookAuditDetails(
                "PROVIDER_WEBHOOK_REJECTED",
                "provider=fake",
                "eventId=evt-provider-mismatch-1",
                "eventType=PAYMENT_SUCCEEDED",
                "status=REJECTED"
        );
    }

    @Test
    void invalidWebhookSecretIsForbiddenAndDoesNotStoreEvent() throws Exception {
        sendWebhookWithSecret("wrong-secret", paymentWebhookJson(
                        "evt-payment-forbidden-1",
                        "payment.succeeded",
                        UUID.randomUUID(),
                        "fake_forbidden_payment",
                        null,
                        "forbidden"
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Invalid provider webhook secret"));

        assertThat(webhookEventRepository.findAll()).isEmpty();
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_AUTH_FAILED");
    }

    @Test
    void oversizedWebhookPayloadIsRejectedBeforeControllerHandling() throws Exception {
        mockMvc.perform(post("/payments/provider-webhooks/fake")
                        .header(FakeProviderWebhookController.WEBHOOK_SECRET_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("a".repeat(65537)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("Payload too large"))
                .andExpect(jsonPath("$.detail").value("Webhook request body is too large"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(webhookEventRepository.findAll()).isEmpty();
    }

    @Test
    void validStripeSignatureProcessesPaymentSuccess() throws Exception {
        String username = "stripe-webhook-payment@example.com";
        PaymentFixture fixture = pendingPayment(username, "stripe");
        when(stripeProviderReadPort.fetchPaymentIntent("pi_stripe_success"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_stripe_success", "succeeded")));
        String requestBody = stripePaymentIntentWebhookJson(
                "evt-stripe-payment-success",
                "payment_intent.succeeded",
                "pi_stripe_success",
                "succeeded",
                fixture.paymentId()
        );

        sendStripeWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerEventId").value("evt-stripe-payment-success"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        ProviderWebhookEventRecord event = webhookEventRepository.findAll().get(0);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pi_stripe_success");
        assertThat(payment.getProviderStatus()).isEqualTo("succeeded");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(event.getProviderName()).isEqualTo("stripe");
        assertThat(event.getProviderEventType()).isEqualTo("payment_intent.succeeded");
        assertThat(event.getProviderObjectId()).isEqualTo("pi_stripe_success");
        assertThat(event.getProviderObjectType()).isEqualTo("payment_intent");
        assertThat(event.getProcessingStatus()).isEqualTo(ProviderWebhookProcessingStatus.PROCESSED);
        assertThat(auditActions()).contains("PAYMENT_SUCCEEDED", "PROVIDER_WEBHOOK_PROCESSED");
    }

    @Test
    void invalidStripeSignatureIsForbiddenAndDoesNotStoreEvent() throws Exception {
        String requestBody = stripePaymentIntentWebhookJson(
                "evt-stripe-invalid-signature",
                "payment_intent.succeeded",
                "pi_invalid_signature",
                "succeeded",
                UUID.randomUUID()
        );

        sendStripeWebhookWithSignature("t=%d,v1=invalid".formatted(Instant.now().getEpochSecond()), requestBody)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Invalid Stripe webhook signature"));

        assertThat(webhookEventRepository.findAll()).isEmpty();
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_AUTH_FAILED");
    }

    @Test
    void missingStripeSignatureIsForbidden() throws Exception {
        String requestBody = stripePaymentIntentWebhookJson(
                "evt-stripe-missing-signature",
                "payment_intent.succeeded",
                "pi_missing_signature",
                "succeeded",
                UUID.randomUUID()
        );

        mockMvc.perform(post("/payments/provider-webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(webhookEventRepository.findAll()).isEmpty();
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_AUTH_FAILED");
    }

    @Test
    void duplicateStripeWebhookEventIsSafeNoop() throws Exception {
        String username = "stripe-webhook-duplicate@example.com";
        PaymentFixture fixture = pendingPayment(username, "stripe");
        when(stripeProviderReadPort.fetchPaymentIntent("pi_stripe_duplicate"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_stripe_duplicate", "succeeded")));
        String requestBody = stripePaymentIntentWebhookJson(
                "evt-stripe-duplicate",
                "payment_intent.succeeded",
                "pi_stripe_duplicate",
                "succeeded",
                fixture.paymentId()
        );

        sendStripeWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
        sendStripeWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("PROVIDER_WEBHOOK_PROCESSED"::equals)).hasSize(1);
    }

    @Test
    void sameStripeEventIdWithDifferentPayloadReturnsConflict() throws Exception {
        String username = "stripe-webhook-conflict@example.com";
        PaymentFixture fixture = pendingPayment(username, "stripe");
        when(stripeProviderReadPort.fetchPaymentIntent("pi_stripe_conflict"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_stripe_conflict", "succeeded")));

        sendStripeWebhook(stripePaymentIntentWebhookJson(
                "evt-stripe-conflict",
                "payment_intent.succeeded",
                "pi_stripe_conflict",
                "succeeded",
                fixture.paymentId()
        )).andExpect(status().isOk());

        sendStripeWebhook(stripePaymentIntentWebhookJson(
                "evt-stripe-conflict",
                "payment_intent.succeeded",
                "pi_stripe_conflict_changed",
                "succeeded",
                fixture.paymentId()
        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_PAYLOAD_CONFLICT");
    }

    @Test
    void unsupportedStripeEventTypeIsIgnoredAndAudited() throws Exception {
        String requestBody = """
                {
                  "id": "evt-stripe-unsupported",
                  "created": %d,
                  "type": "customer.created",
                  "data": {
                    "object": {
                      "id": "cus_unsupported",
                      "object": "customer"
                    }
                  }
                }
                """.formatted(Instant.now().getEpochSecond());

        sendStripeWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        ProviderWebhookEventRecord event = webhookEventRepository.findAll().get(0);
        assertThat(event.getProcessingStatus()).isEqualTo(ProviderWebhookProcessingStatus.IGNORED);
        assertThat(event.getEventType().name()).isEqualTo("UNSUPPORTED");
        assertThat(auditActions()).contains("PROVIDER_WEBHOOK_IGNORED");
    }

    @Test
    void stripeFailedEventAfterLocalSuccessIsIgnored() throws Exception {
        PaymentFixture fixture = successfulStripePayment(
                "stripe-webhook-failed-after-success@example.com",
                "pi_already_succeeded"
        );
        when(stripeProviderReadPort.fetchPaymentIntent("pi_already_succeeded"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_already_succeeded", "requires_payment_method")));

        sendStripeWebhook(stripePaymentIntentWebhookJson(
                "evt-stripe-failed-after-success",
                "payment_intent.payment_failed",
                "pi_already_succeeded",
                "requires_payment_method",
                fixture.paymentId()
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions()).doesNotContain("PAYMENT_FAILED");
    }

    @Test
    void failedStripeEventUsesCurrentProviderSuccessToCompletePaymentOnce() throws Exception {
        String username = "stripe-webhook-failed-current-success@example.com";
        PaymentFixture fixture = pendingPayment(username, "stripe");
        when(stripeProviderReadPort.fetchPaymentIntent("pi_current_success"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_current_success", "succeeded")));

        sendStripeWebhook(stripePaymentIntentWebhookJson(
                "evt-stripe-failed-current-success",
                "payment_intent.payment_failed",
                "pi_current_success",
                "requires_payment_method",
                fixture.paymentId()
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pi_current_success");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions()).contains("PAYMENT_SUCCEEDED", "PROVIDER_WEBHOOK_PROCESSED");
    }

    @Test
    void stripeRefundSucceededWebhookCompletesPendingRefund() throws Exception {
        PaymentFixture fixture = successfulStripePayment(
                "stripe-webhook-refund@example.com",
                "pi_refund_payment"
        );
        PaymentRecord payment = paymentRepository.findWithOrderById(fixture.paymentId()).orElseThrow();
        RefundRecord refund = refundRepository.saveAndFlush(RefundRecord.pending(
                payment,
                "stripe-refund-webhook-key",
                providerRefundIdempotencyKey(payment, "stripe-refund-webhook-key"),
                "customer_request"
        ));
        when(stripeProviderReadPort.fetchRefund("re_stripe_success"))
                .thenReturn(Optional.of(stripeRefund("re_stripe_success", "succeeded")));
        String requestBody = stripeRefundUpdatedWebhookJson(
                "evt-stripe-refund-success",
                "re_stripe_success",
                "succeeded",
                refund.getId()
        );

        sendStripeWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
        sendStripeWebhook(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        PaymentRecord updatedPayment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        RefundRecord updatedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(updatedRefund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(updatedRefund.getProviderRefundId()).isEqualTo("re_stripe_success");
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refundTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("PROVIDER_WEBHOOK_PROCESSED"::equals)).hasSize(1);
    }

    @Test
    void concurrentStripeSameEventDeliveryProcessesOnce() throws Exception {
        String username = "stripe-webhook-concurrent@example.com";
        PaymentFixture fixture = pendingPayment(username, "stripe");
        when(stripeProviderReadPort.fetchPaymentIntent("pi_stripe_concurrent"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_stripe_concurrent", "succeeded")));
        String requestBody = stripePaymentIntentWebhookJson(
                "evt-stripe-concurrent",
                "payment_intent.succeeded",
                "pi_stripe_concurrent",
                "succeeded",
                fixture.paymentId()
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> delivery = () -> {
                start.await(5, TimeUnit.SECONDS);
                return sendStripeWebhook(requestBody)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> first = executor.submit(delivery);
            Future<Integer> second = executor.submit(delivery);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsOnly(200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(paymentCaptureTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("PROVIDER_WEBHOOK_PROCESSED"::equals)).hasSize(1);
    }

    @Test
    void refundSucceededWebhookCompletesPendingRefundAndPostsReversal() throws Exception {
        String username = "webhook-refund@example.com";
        PaymentFixture fixture = successfulPayment(username);
        PaymentRecord payment = paymentRepository.findWithOrderById(fixture.paymentId()).orElseThrow();
        RefundRecord refund = refundRepository.saveAndFlush(RefundRecord.pending(
                payment,
                "refund-webhook-key",
                providerRefundIdempotencyKey(payment, "refund-webhook-key"),
                "customer_request"
        ));
        String providerRefundId = "fake_webhook_refund_" + UUID.randomUUID();

        sendWebhook(refundWebhookJson(
                        "evt-refund-success-1",
                        "refund.succeeded",
                        refund.getId(),
                        providerRefundId,
                        null,
                        "refund completed by webhook"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        PaymentRecord updatedPayment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        RefundRecord updatedRefund = refundRepository.findById(refund.getId()).orElseThrow();

        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(updatedRefund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(updatedRefund.getProviderRefundId()).isEqualTo(providerRefundId);
        assertThat(refundTransactions()).hasSize(1);
        assertThat(auditActions()).contains("REFUND_SUCCEEDED", "PROVIDER_WEBHOOK_PROCESSED");
    }

    private ResultActions sendWebhook(String requestBody) throws Exception {
        return sendWebhookWithSecret(WEBHOOK_SECRET, requestBody);
    }

    private ResultActions sendWebhookWithSecret(String secret, String requestBody) throws Exception {
        return mockMvc.perform(post("/payments/provider-webhooks/fake")
                .header(FakeProviderWebhookController.WEBHOOK_SECRET_HEADER, secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private ResultActions sendStripeWebhook(String requestBody) throws Exception {
        return sendStripeWebhookWithSignature(stripeSignature(requestBody), requestBody);
    }

    private ResultActions sendStripeWebhookWithSignature(String signature, String requestBody) throws Exception {
        return mockMvc.perform(post("/payments/provider-webhooks/stripe")
                .header(StripeProviderWebhookController.STRIPE_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private PaymentFixture pendingPayment(String username) throws Exception {
        return pendingPayment(username, "fake");
    }

    private PaymentFixture pendingPayment(String username, String providerCode) throws Exception {
        UUID orderId = pendingOrder(username);
        CustomerOrderRecord order = orderRepository.findWithCustomerById(orderId).orElseThrow();
        String idempotencyKey = "pending-webhook-payment-" + UUID.randomUUID();
        PaymentRecord payment = paymentRepository.saveAndFlush(PaymentRecord.pending(
                order,
                idempotencyKey,
                providerCode,
                providerPaymentIdempotencyKey(order, providerCode, idempotencyKey)
        ));
        return new PaymentFixture(orderId, payment.getId(), null);
    }

    private PaymentFixture successfulStripePayment(String username, String providerPaymentId) throws Exception {
        PaymentFixture fixture = pendingPayment(username, "stripe");
        paymentAttemptService.completeAttempt(
                fixture.paymentId(),
                PaymentProviderResult.succeeded(providerPaymentId, "succeeded", "seeded Stripe payment"),
                null
        );
        return new PaymentFixture(fixture.orderId(), fixture.paymentId(), providerPaymentId);
    }

    private PaymentFixture successfulPayment(String username) throws Exception {
        UUID orderId = pendingOrder(username);
        MvcResult paymentResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "payment-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, "pm_approved"))
                        .with(customerJwt(username, "payment:create")))
                .andExpect(status().isOk())
                .andReturn();

        String paymentId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.paymentId");
        String providerPaymentId = JsonPath.read(
                paymentResult.getResponse().getContentAsString(),
                "$.providerPaymentId"
        );
        return new PaymentFixture(orderId, UUID.fromString(paymentId), providerPaymentId);
    }

    private UUID pendingOrder(String username) throws Exception {
        user(username);
        ProductModel product = product("Webhook Product", 10);
        long cartId = createCart(username);
        addItem(username, cartId, product.getProductId(), 2);
        return checkout(username, cartId);
    }

    private long createCart(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/cart")
                        .with(customerJwt(username, "cart:write")))
                .andExpect(status().isOk())
                .andReturn();

        Number cartId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartId");
        return cartId.longValue();
    }

    private void addItem(String username, long cartId, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/cart/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": %d
                                }
                                """.formatted(productId, quantity))
                        .with(customerJwt(username, "cart:write")))
                .andExpect(status().isOk());
    }

    private UUID checkout(String username, long cartId) throws Exception {
        MvcResult result = mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartId": %d
                                }
                                """.formatted(cartId))
                        .with(customerJwt(username, "checkout:write")))
                .andExpect(status().isOk())
                .andReturn();

        String orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
        return UUID.fromString(orderId);
    }

    private UserModel user(String username) {
        return userRepo.save(UserModel.builder()
                .identitySubject(username)
                .username(username)
                .email(username)
                .firstName("Webhook")
                .lastName("Customer")
                .build());
    }

    private ProductModel product(String name, int availableQuantity) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name(name)
                .price(new java.math.BigDecimal("10.00"))
                .active(true)
                .build());
        inventoryRepository.save(new InventoryRecord(product.getProductId(), availableQuantity, 0));
        return product;
    }

    private RequestPostProcessor customerJwt(String username, String scope) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(username)
                        .claim("preferred_username", username)
                        .claim("email", username))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("SCOPE_" + scope)
                );
    }

    private String paymentJson(UUID orderId, String paymentMethodToken) {
        return """
                {
                  "orderId": "%s",
                  "paymentMethodToken": "%s"
                }
                """.formatted(orderId, paymentMethodToken);
    }

    private String paymentWebhookJson(
            String eventId,
            String type,
            UUID paymentId,
            String providerPaymentId,
            String failureCode,
            String message
    ) {
        return """
                {
                  "eventId": "%s",
                  "type": "%s",
                  "paymentId": "%s",
                  "providerPaymentId": "%s",
                  "failureCode": %s,
                  "message": "%s"
                }
                """.formatted(eventId, type, paymentId, providerPaymentId, jsonStringOrNull(failureCode), message);
    }

    private String stripePaymentIntentWebhookJson(
            String eventId,
            String type,
            String providerPaymentId,
            String status,
            UUID paymentId
    ) {
        return """
                {
                  "id": "%s",
                  "created": %d,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "status": "%s",
                      "last_payment_error": {
                        "decline_code": "card_declined",
                        "code": "card_declined"
                      },
                      "metadata": {
                        "internalPaymentId": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                eventId,
                Instant.now().getEpochSecond(),
                type,
                providerPaymentId,
                status,
                paymentId
        );
    }

    private String stripeRefundUpdatedWebhookJson(
            String eventId,
            String providerRefundId,
            String status,
            UUID refundId
    ) {
        return """
                {
                  "id": "%s",
                  "created": %d,
                  "type": "refund.updated",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "refund",
                      "payment_intent": "pi_refund_payment",
                      "status": "%s",
                      "metadata": {
                        "internalRefundId": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                eventId,
                Instant.now().getEpochSecond(),
                providerRefundId,
                status,
                refundId
        );
    }

    private String refundWebhookJson(
            String eventId,
            String type,
            UUID refundId,
            String providerRefundId,
            String failureCode,
            String message
    ) {
        return """
                {
                  "eventId": "%s",
                  "type": "%s",
                  "refundId": "%s",
                  "providerRefundId": "%s",
                  "failureCode": %s,
                  "message": "%s"
                }
                """.formatted(eventId, type, refundId, providerRefundId, jsonStringOrNull(failureCode), message);
    }

    private String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
    }

    private StripePaymentIntentSnapshot stripePaymentIntent(String providerPaymentId, String status) {
        return new StripePaymentIntentSnapshot(
                providerPaymentId,
                status,
                "stripe_card_declined",
                "Stripe PaymentIntent current status is " + status
        );
    }

    private StripeRefundSnapshot stripeRefund(String providerRefundId, String status) {
        return new StripeRefundSnapshot(
                providerRefundId,
                status,
                "stripe_refund_failed",
                "Stripe refund current status is " + status
        );
    }

    private String stripeSignature(String requestBody) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + requestBody;
        return "t=%d,v1=%s".formatted(timestamp, hmacHex(signedPayload));
    }

    private String hmacHex(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(STRIPE_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String providerPaymentIdempotencyKey(
            CustomerOrderRecord order,
            String providerCode,
            String idempotencyKey
    ) {
        return "payment:%s:%d:%s:%s".formatted(
                providerCode,
                order.getCustomer().getId(),
                order.getId(),
                idempotencyKey
        );
    }

    private String providerRefundIdempotencyKey(PaymentRecord payment, String idempotencyKey) {
        return "refund:%s:%d:%s:%s".formatted(
                payment.getProviderCode(),
                payment.getCustomerId(),
                payment.getId(),
                idempotencyKey
        );
    }

    private List<LedgerTransactionRecord> paymentCaptureTransactions() {
        return ledgerTransactionRepository.findAll()
                .stream()
                .filter(transaction -> transaction.getTransactionType() == LedgerTransactionType.PAYMENT_CAPTURE)
                .toList();
    }

    private List<LedgerTransactionRecord> refundTransactions() {
        return ledgerTransactionRepository.findAll()
                .stream()
                .filter(transaction -> transaction.getTransactionType() == LedgerTransactionType.REFUND)
                .toList();
    }

    private void truncateLedger() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entry, ledger_transaction RESTART IDENTITY");
    }

    private List<String> auditActions() {
        return auditEventRepository.findAll()
                .stream()
                .map(AuditEventRecord::getAction)
                .toList();
    }

    private void assertWebhookAuditDetails(String action, String... fragments) {
        List<String> details = auditEventRepository.findAll()
                .stream()
                .filter(event -> action.equals(event.getAction()))
                .map(AuditEventRecord::getDetails)
                .toList();

        assertThat(details).isNotEmpty();
        assertThat(details)
                .anySatisfy(value -> assertThat(value).contains(fragments));
    }

    private record PaymentFixture(UUID orderId, UUID paymentId, String providerPaymentId) {
    }
}
