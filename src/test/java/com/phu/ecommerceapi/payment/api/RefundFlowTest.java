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
import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.ledger.infrastructure.JpaPaymentLedgerPostingAdapter;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundFlowTest {

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
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
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
        resetData();
    }

    @Test
    void customerCanRefundOwnSuccessfulPaymentAndPostsReversingLedger() throws Exception {
        String username = "refund-customer@example.com";
        PaymentFixture fixture = successfulPayment(username);

        MvcResult result = createRefund(username, fixture.paymentId(), "refund-key-1", refundJson("customer requested"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").exists())
                .andExpect(jsonPath("$.paymentId").value(fixture.paymentId().toString()))
                .andExpect(jsonPath("$.orderId").value(fixture.orderId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerRefundId").isNotEmpty())
                .andExpect(jsonPath("$.failureCode").doesNotExist())
                .andExpect(jsonPath("$.amount").value(20.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));
        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        RefundRecord refund = refundRepository.findById(refundId).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertRefundLedgerReversesPayment(refund);
        assertThat(auditActions()).contains("PAYMENT_SUCCEEDED", "REFUND_SUCCEEDED");
    }

    @Test
    void repeatedRefundRequestReturnsSameStableResponse() throws Exception {
        String username = "refund-replay@example.com";
        PaymentFixture fixture = successfulPayment(username);
        String requestBody = refundJson("customer requested");

        MvcResult firstResult = createRefund(username, fixture.paymentId(), "refund-key-2", requestBody)
                .andExpect(status().isOk())
                .andReturn();
        MvcResult replayResult = createRefund(username, fixture.paymentId(), "refund-key-2", requestBody)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(replayResult.getResponse().getContentAsString())
                .isEqualTo(firstResult.getResponse().getContentAsString());
        assertThat(refundRepository.findAll()).hasSize(1);
        assertThat(refundLedgerTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("REFUND_SUCCEEDED"::equals)).hasSize(1);
    }

    @Test
    void failedPaymentCannotBeRefunded() throws Exception {
        String username = "refund-failed-payment@example.com";
        PaymentFixture fixture = failedPayment(username);

        createRefund(username, fixture.paymentId(), "refund-key-3", refundJson("customer requested"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("Payment is not refundable"));

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(refundRepository.findAll()).isEmpty();
        assertThat(refundLedgerTransactions()).isEmpty();
    }

    @Test
    void crossCustomerRefundIsDeniedAndDoesNotCreateRefund() throws Exception {
        String owner = "refund-owner@example.com";
        String attacker = "refund-attacker@example.com";
        PaymentFixture fixture = successfulPayment(owner);
        user(attacker);

        createRefund(attacker, fixture.paymentId(), "refund-key-4", refundJson("customer requested"))
                .andExpect(status().isForbidden());

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(refundRepository.findAll()).isEmpty();
        assertThat(refundLedgerTransactions()).isEmpty();
    }

    @Test
    void concurrentDoubleRefundAllowsOnlyOneSuccess() throws Exception {
        String username = "refund-concurrent@example.com";
        PaymentFixture fixture = successfulPayment(username);
        String requestBody = refundJson("customer requested");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> refundStatusWhenReleased(
                    username,
                    fixture.paymentId(),
                    "refund-concurrent-key-1",
                    requestBody,
                    ready,
                    start
            ));
            Future<Integer> second = executor.submit(() -> refundStatusWhenReleased(
                    username,
                    fixture.paymentId(),
                    "refund-concurrent-key-2",
                    requestBody,
                    ready,
                    start
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        PaymentRecord payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        CustomerOrderRecord order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refundRepository.findAll()).hasSize(1);
        assertThat(refundLedgerTransactions()).hasSize(1);
        assertThat(auditActions().stream().filter("REFUND_SUCCEEDED"::equals)).hasSize(1);
    }

    private int refundStatusWhenReleased(
            String username,
            UUID paymentId,
            String idempotencyKey,
            String requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Refund concurrency start signal timed out");
        }
        return createRefund(username, paymentId, idempotencyKey, requestBody)
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private ResultActions createRefund(
            String username,
            UUID paymentId,
            String idempotencyKey,
            String requestBody
    ) throws Exception {
        return mockMvc.perform(post("/payments/{paymentId}/refunds", paymentId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(customerJwt(username, "payment:refund")));
    }

    private PaymentFixture successfulPayment(String username) throws Exception {
        return createPayment(username, "pm_approved");
    }

    private PaymentFixture failedPayment(String username) throws Exception {
        return createPayment(username, "pm_card_declined");
    }

    private PaymentFixture createPayment(String username, String paymentMethodToken) throws Exception {
        user(username);
        ProductModel product = product("Refund Product", 10);
        long cartId = createCart(username);
        addItem(username, cartId, product.getProductId(), 2);
        UUID orderId = checkout(username, cartId);

        MvcResult paymentResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "payment-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, paymentMethodToken))
                        .with(customerJwt(username, "payment:create")))
                .andExpect(status().isOk())
                .andReturn();

        String paymentId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.paymentId");
        return new PaymentFixture(orderId, UUID.fromString(paymentId));
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
                .firstName("Refund")
                .lastName("Customer")
                .build());
    }

    private ProductModel product(String name, int availableQuantity) {
        ProductModel product = productRepo.save(ProductModel.builder()
                .name(name)
                .price(new java.math.BigDecimal("10.00"))
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

    private String refundJson(String reason) {
        return """
                {
                  "reason": "%s"
                }
                """.formatted(reason);
    }

    private void assertRefundLedgerReversesPayment(RefundRecord refund) {
        LedgerTransactionRecord transaction = ledgerTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        "REFUND",
                        refund.getId().toString(),
                        LedgerTransactionType.REFUND
                )
                .orElseThrow();
        List<LedgerEntryRecord> entries = ledgerEntryRepository.findByTransactionId(transaction.getId());
        Map<String, String> directionByAccount = ledgerDirectionsByAccount(transaction.getId());

        assertThat(entries).hasSize(2);
        assertThat(total(entries, LedgerEntryDirection.DEBIT)).isEqualByComparingTo(refund.getAmount());
        assertThat(total(entries, LedgerEntryDirection.CREDIT)).isEqualByComparingTo(refund.getAmount());
        assertThat(entries).extracting(LedgerEntryRecord::getCurrency).containsOnly(refund.getCurrency());
        assertThat(directionByAccount)
                .containsEntry(JpaPaymentLedgerPostingAdapter.ORDER_REVENUE_ACCOUNT, "DEBIT")
                .containsEntry(JpaPaymentLedgerPostingAdapter.PROVIDER_CLEARING_ACCOUNT, "CREDIT");
    }

    private BigDecimal total(List<LedgerEntryRecord> entries, LedgerEntryDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(LedgerEntryRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, String> ledgerDirectionsByAccount(UUID transactionId) {
        return jdbcTemplate.query(
                """
                        SELECT account.code, entry.direction
                        FROM ledger_entry entry
                        JOIN ledger_account account ON account.id = entry.account_id
                        WHERE entry.transaction_id = ?
                        """,
                resultSet -> {
                    Map<String, String> directions = new HashMap<>();
                    while (resultSet.next()) {
                        directions.put(resultSet.getString("code"), resultSet.getString("direction"));
                    }
                    return directions;
                },
                transactionId
        );
    }

    private List<LedgerTransactionRecord> refundLedgerTransactions() {
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

    private record PaymentFixture(UUID orderId, UUID paymentId) {
    }
}
