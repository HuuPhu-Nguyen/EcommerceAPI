package com.phu.ecommerceapi.payment.api;

import com.jayway.jsonpath.JsonPath;
import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderWebhookHandlingTest {

    private static final String WEBHOOK_SECRET = "local-fake-webhook-secret";

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

    @BeforeEach
    void resetData() {
        truncateLedger();
        webhookEventRepository.deleteAll();
        auditEventRepository.deleteAll();
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
        assertThat(event.getProcessingStatus()).isEqualTo(ProviderWebhookProcessingStatus.PROCESSED);
        assertThat(auditActions()).contains("PAYMENT_SUCCEEDED", "PROVIDER_WEBHOOK_PROCESSED");
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
    void refundSucceededWebhookCompletesPendingRefundAndPostsReversal() throws Exception {
        String username = "webhook-refund@example.com";
        PaymentFixture fixture = successfulPayment(username);
        PaymentRecord payment = paymentRepository.findWithOrderById(fixture.paymentId()).orElseThrow();
        RefundRecord refund = refundRepository.saveAndFlush(RefundRecord.pending(
                payment,
                "refund-webhook-key",
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

    private PaymentFixture pendingPayment(String username) throws Exception {
        UUID orderId = pendingOrder(username);
        CustomerOrderRecord order = orderRepository.findWithCustomerById(orderId).orElseThrow();
        PaymentRecord payment = paymentRepository.saveAndFlush(PaymentRecord.pending(
                order,
                "pending-webhook-payment-" + UUID.randomUUID()
        ));
        return new PaymentFixture(orderId, payment.getId(), null);
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
                .username(username)
                .email(username)
                .firstName("Webhook")
                .lastName("Customer")
                .build());
    }

    private ProductModel product(String name, int availableQuantity) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name(name)
                .price(10.00)
                .stock(availableQuantity)
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

    private record PaymentFixture(UUID orderId, UUID paymentId, String providerPaymentId) {
    }
}
