package com.phu.ecommerceapi.payment.api;

import com.jayway.jsonpath.JsonPath;
import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRecord;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecord;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.StripePaymentGateway;
import com.phu.ecommerceapi.payment.infrastructure.StripePaymentIntentCreateRequest;
import com.phu.ecommerceapi.payment.infrastructure.StripePaymentIntentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.payment-provider.active=stripe",
        "app.payment-provider.enabled=fake,stripe",
        "app.stripe.secret-key=sk_test_safe_placeholder",
        "app.stripe.webhook-secret=whsec_test_safe_placeholder"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreatePaymentMultiAttemptConcurrencyTest {

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
    private PaymentIdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BlockingStripePaymentGateway stripeGateway;

    @BeforeEach
    void resetData() {
        stripeGateway.reset();
        truncateLedger();
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
        stripeGateway.releasePayments();
        resetData();
    }

    @Test
    void failedFakeAttemptCanBeFollowedByDifferentEnabledProvider() throws Exception {
        String username = "payment-provider-switch-after-failure@example.com";
        UUID orderId = pendingOrder(username);

        createPayment(username, "payment-switch-key-1", orderId, "fake", "pm_card_declined")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("fake"))
                .andExpect(jsonPath("$.status").value("FAILED"));

        CustomerOrderRecord afterFailure = orderRepository.findById(orderId).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        createPayment(username, "payment-switch-key-2", orderId, "stripe", "pm_approved")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("stripe"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        CustomerOrderRecord afterRetry = orderRepository.findById(orderId).orElseThrow();
        assertThat(afterRetry.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findAll())
                .extracting(PaymentRecord::getProviderCode, PaymentRecord::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("fake", PaymentStatus.FAILED),
                        org.assertj.core.groups.Tuple.tuple("stripe", PaymentStatus.SUCCEEDED)
                );
        assertThat(stripeGateway.paymentCalls()).isEqualTo(1);
        assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
    }

    @Test
    void refundAfterFakePaymentUsesOriginalProviderEvenWhenStripeIsActive() throws Exception {
        String username = "refund-original-provider@example.com";
        UUID orderId = pendingOrder(username);
        MvcResult paymentResult = createPayment(
                username,
                "payment-original-provider-key",
                orderId,
                "fake",
                "pm_approved"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("fake"))
                .andReturn();
        UUID paymentId = UUID.fromString(JsonPath.read(
                paymentResult.getResponse().getContentAsString(),
                "$.paymentId"
        ));

        MvcResult refundResult = createRefund(
                username,
                "refund-original-provider-key",
                paymentId,
                refundJson("stripe", "customer requested")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("fake"))
                .andReturn();

        String providerRefundId = JsonPath.read(refundResult.getResponse().getContentAsString(), "$.providerRefundId");
        RefundRecord refund = refundRepository.findAll().getFirst();
        assertThat(providerRefundId).startsWith("fake_refund_");
        assertThat(refund.getProviderCode()).isEqualTo("fake");
        assertThat(stripeGateway.paymentCalls()).isZero();
        assertThat(ledgerTransactionRepository.findAll()).hasSize(2);
    }

    @Test
    void concurrentAttemptsWithDifferentIdempotencyKeysCallProviderOnce() throws Exception {
        String username = "payment-concurrent-same-provider@example.com";
        UUID orderId = pendingOrder(username);
        stripeGateway.blockPayments();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<MvcResult> firstAttempt = executor.submit(() -> createPayment(
                    username,
                    "payment-concurrent-key-1",
                    orderId,
                    "stripe",
                    "pm_approved"
            )
                    .andExpect(status().isOk())
                    .andReturn());

            assertThat(stripeGateway.awaitPaymentCall()).isTrue();

            createPayment(username, "payment-concurrent-key-2", orderId, "stripe", "pm_approved")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.detail").value("Order already has a payment attempt in progress"));

            stripeGateway.releasePayments();
            firstAttempt.get(5, TimeUnit.SECONDS);
        } finally {
            stripeGateway.releasePayments();
            executor.shutdownNow();
        }

        assertThat(stripeGateway.paymentCalls()).isEqualTo(1);
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(idempotencyRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void concurrentAttemptsWithDifferentProvidersCallOnlyTheFirstProvider() throws Exception {
        String username = "payment-concurrent-different-provider@example.com";
        UUID orderId = pendingOrder(username);
        stripeGateway.blockPayments();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<MvcResult> firstAttempt = executor.submit(() -> createPayment(
                    username,
                    "payment-concurrent-provider-key-1",
                    orderId,
                    "stripe",
                    "pm_approved"
            )
                    .andExpect(status().isOk())
                    .andReturn());

            assertThat(stripeGateway.awaitPaymentCall()).isTrue();

            createPayment(username, "payment-concurrent-provider-key-2", orderId, "fake", "pm_approved")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.detail").value("Order already has a payment attempt in progress"));

            stripeGateway.releasePayments();
            firstAttempt.get(5, TimeUnit.SECONDS);
        } finally {
            stripeGateway.releasePayments();
            executor.shutdownNow();
        }

        PaymentRecord payment = paymentRepository.findAll().getFirst();
        assertThat(stripeGateway.paymentCalls()).isEqualTo(1);
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(payment.getProviderCode()).isEqualTo("stripe");
        assertThat(idempotencyRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void replayAfterLinkedStripeAttemptWithIncompleteIdempotencyDoesNotCreateSecondPaymentIntent() throws Exception {
        String username = "payment-stripe-replay-local-attempt@example.com";
        UUID orderId = pendingOrder(username);
        String idempotencyKey = "payment-stripe-replay-key";
        String requestBody = paymentJson(orderId, "stripe", "pm_approved");

        MvcResult firstResult = createPayment(username, idempotencyKey, requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("stripe"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerStatus").value("succeeded"))
                .andExpect(jsonPath("$.providerPaymentId").value(org.hamcrest.Matchers.startsWith("pi_test_")))
                .andReturn();

        PaymentRecord payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        PaymentIdempotencyRecord idempotency = paymentIdempotency(payment, idempotencyKey);
        markIdempotencyInProgress(idempotency);

        MvcResult replayResult = createPayment(username, idempotencyKey, requestBody)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(replayResult.getResponse().getContentAsString())
                .isEqualTo(firstResult.getResponse().getContentAsString());
        assertThat(stripeGateway.paymentCalls()).isEqualTo(1);
        assertThat(idempotencyRepository.findById(idempotency.getId()).orElseThrow().isCompleted()).isTrue();
    }

    @Test
    void pendingStripeIntentReplaysFromLocalAttemptWithoutSecondPaymentIntent() throws Exception {
        String username = "payment-stripe-pending-replay@example.com";
        UUID orderId = pendingOrder(username);
        String idempotencyKey = "payment-stripe-pending-key";
        String requestBody = paymentJson(orderId, "stripe", "pm_processing");

        MvcResult firstResult = createPayment(username, idempotencyKey, requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("stripe"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerStatus").value("processing"))
                .andExpect(jsonPath("$.providerPaymentId").value(org.hamcrest.Matchers.startsWith("pi_test_")))
                .andReturn();

        PaymentRecord payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        PaymentIdempotencyRecord idempotency = paymentIdempotency(payment, idempotencyKey);
        markIdempotencyInProgress(idempotency);

        MvcResult replayResult = createPayment(username, idempotencyKey, requestBody)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(replayResult.getResponse().getContentAsString())
                .isEqualTo(firstResult.getResponse().getContentAsString());
        assertThat(stripeGateway.paymentCalls()).isEqualTo(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(ledgerTransactionRepository.findAll()).isEmpty();
    }

    @Test
    void stripeTimeoutBlocksSecondAttemptUntilReconciliation() throws Exception {
        String username = "payment-stripe-timeout-blocks@example.com";
        UUID orderId = pendingOrder(username);

        createPayment(username, "payment-stripe-timeout-key-1", orderId, "stripe", "pm_provider_timeout")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.provider").value("stripe"))
                .andExpect(jsonPath("$.status").value("PROVIDER_TIMEOUT"))
                .andExpect(jsonPath("$.failureCode").value("provider_timeout"));

        createPayment(username, "payment-stripe-timeout-key-2", orderId, "stripe", "pm_approved")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order has a payment attempt with unknown provider outcome"));

        PaymentRecord payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROVIDER_TIMEOUT);
        assertThat(stripeGateway.paymentCalls()).isEqualTo(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private ResultActions createPayment(
            String username,
            String idempotencyKey,
            UUID orderId,
            String provider,
            String paymentMethodToken
    ) throws Exception {
        return mockMvc.perform(post("/payments")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson(orderId, provider, paymentMethodToken))
                .with(customerJwt(username, "payment:create")));
    }

    private ResultActions createPayment(
            String username,
            String idempotencyKey,
            String requestBody
    ) throws Exception {
        return mockMvc.perform(post("/payments")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(customerJwt(username, "payment:create")));
    }

    private ResultActions createRefund(
            String username,
            String idempotencyKey,
            UUID paymentId,
            String requestBody
    ) throws Exception {
        return mockMvc.perform(post("/payments/{paymentId}/refunds", paymentId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(customerJwt(username, "payment:refund")));
    }

    private UUID pendingOrder(String username) throws Exception {
        user(username);
        ProductModel product = product("Multi Attempt Product", 10);
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
                .firstName("Payment")
                .lastName("Customer")
                .build());
    }

    private ProductModel product(String name, int availableQuantity) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name(name)
                .price(new BigDecimal("10.00"))
                .currency("USD")
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

    private String paymentJson(UUID orderId, String provider, String paymentMethodToken) {
        return """
                {
                  "orderId": "%s",
                  "provider": "%s",
                  "paymentMethodToken": "%s"
                }
                """.formatted(orderId, provider, paymentMethodToken);
    }

    private String refundJson(String requestedProvider, String reason) {
        return """
                {
                  "provider": "%s",
                  "reason": "%s"
                }
                """.formatted(requestedProvider, reason);
    }

    private void truncateLedger() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entry, ledger_transaction RESTART IDENTITY");
    }

    private PaymentIdempotencyRecord paymentIdempotency(PaymentRecord payment, String idempotencyKey) {
        return idempotencyRepository.findByCustomerIdAndEndpointAndOperationAndIdempotencyKey(
                payment.getCustomerId(),
                "/payments",
                "CREATE_PAYMENT",
                idempotencyKey
        ).orElseThrow();
    }

    private void markIdempotencyInProgress(PaymentIdempotencyRecord idempotency) {
        jdbcTemplate.update("""
                UPDATE payment_idempotency_record
                SET status = 'IN_PROGRESS',
                    response_status = NULL,
                    response_body = NULL,
                    completed_at = NULL
                WHERE id = ?
                """, idempotency.getId());
    }

    @TestConfiguration
    static class StripeProviderTestConfig {

        @Bean
        @Primary
        BlockingStripePaymentGateway blockingStripePaymentGateway() {
            return new BlockingStripePaymentGateway();
        }
    }

    static final class BlockingStripePaymentGateway implements StripePaymentGateway {

        private final AtomicInteger paymentCalls = new AtomicInteger();
        private CountDownLatch paymentCallEntered = new CountDownLatch(1);
        private CountDownLatch releasePayments = new CountDownLatch(0);
        private boolean blockPayments;

        @Override
        public StripePaymentIntentResult createPaymentIntent(StripePaymentIntentCreateRequest request) {
            paymentCalls.incrementAndGet();
            paymentCallEntered.countDown();
            awaitReleaseIfBlocked();
            String paymentIntentId = providerPaymentIntentId(request);
            return switch (request.paymentMethodToken()) {
                case "pm_card_declined" ->
                        new StripePaymentIntentResult(paymentIntentId, "requires_payment_method", "card_declined");
                case "pm_processing" -> new StripePaymentIntentResult(paymentIntentId, "processing", null);
                case "pm_requires_action" -> new StripePaymentIntentResult(paymentIntentId, "requires_action", null);
                case "pm_provider_timeout" -> throw new PaymentProviderTimeoutException(
                        "Stripe payment provider timed out for payment " + request.paymentId()
                );
                default -> new StripePaymentIntentResult(paymentIntentId, "succeeded", null);
            };
        }

        void reset() {
            paymentCalls.set(0);
            paymentCallEntered = new CountDownLatch(1);
            releasePayments = new CountDownLatch(0);
            blockPayments = false;
        }

        void blockPayments() {
            paymentCallEntered = new CountDownLatch(1);
            releasePayments = new CountDownLatch(1);
            blockPayments = true;
        }

        boolean awaitPaymentCall() throws InterruptedException {
            return paymentCallEntered.await(5, TimeUnit.SECONDS);
        }

        void releasePayments() {
            releasePayments.countDown();
        }

        int paymentCalls() {
            return paymentCalls.get();
        }

        private void awaitReleaseIfBlocked() {
            if (!blockPayments) {
                return;
            }
            try {
                if (!releasePayments.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release test Stripe payment");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release test Stripe payment", exception);
            }
        }

        private String providerPaymentIntentId(StripePaymentIntentCreateRequest request) {
            return "pi_test_" + request.paymentId();
        }
    }
}
