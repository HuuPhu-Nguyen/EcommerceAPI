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
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreatePaymentUseCaseTest {

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
    void customerCanCreatePaymentForOwnedPendingOrder() throws Exception {
        String username = "payment-customer@example.com";
        UUID orderId = pendingOrder(username);

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "payment-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, "pm_approved"))
                        .with(customerJwt(username, "payment:create")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerPaymentId").isNotEmpty())
                .andExpect(jsonPath("$.failureCode").doesNotExist())
                .andExpect(jsonPath("$.amount").value(20.00))
                .andExpect(jsonPath("$.currency").value("USD"));

        CustomerOrderRecord order = orderRepository.findById(orderId).orElseThrow();
        PaymentRecord payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).startsWith("fake_");
        assertBalancedPaymentLedger(payment);
        assertThat(auditActions()).contains("CHECKOUT_ORDER_CREATED", "PAYMENT_SUCCEEDED");
    }

    @Test
    void repeatedIdempotentPaymentRequestReturnsSameStableResponse() throws Exception {
        String username = "payment-replay@example.com";
        UUID orderId = pendingOrder(username);
        String requestBody = paymentJson(orderId, "pm_approved");

        MvcResult firstResult = createPayment(username, "payment-key-2", requestBody)
                .andExpect(status().isOk())
                .andReturn();
        MvcResult replayResult = createPayment(username, "payment-key-2", requestBody)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(replayResult.getResponse().getContentAsString())
                .isEqualTo(firstResult.getResponse().getContentAsString());
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(auditActions().stream().filter("PAYMENT_SUCCEEDED"::equals)).hasSize(1);
    }

    @Test
    void sameIdempotencyKeyWithDifferentBodyReturnsConflictBeforeOrderStateCheck() throws Exception {
        String username = "payment-conflict@example.com";
        UUID orderId = pendingOrder(username);

        createPayment(username, "payment-key-3", paymentJson(orderId, "pm_approved"))
                .andExpect(status().isOk());

        createPayment(username, "payment-key-3", paymentJson(orderId, "pm_card_declined"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("Idempotency key was reused with a different request body"));
    }

    @Test
    void providerFailurePersistsFailedPaymentAndDoesNotMarkOrderPaid() throws Exception {
        String username = "payment-failed@example.com";
        UUID orderId = pendingOrder(username);

        createPayment(username, "payment-key-4", paymentJson(orderId, "pm_card_declined"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.providerStatus").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("fake_declined"));

        CustomerOrderRecord order = orderRepository.findById(orderId).orElseThrow();
        PaymentRecord payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(ledgerTransactionRepository.findAll()).isEmpty();
        assertThat(auditActions()).contains("PAYMENT_FAILED");
        assertThat(auditActions()).doesNotContain("PAYMENT_SUCCEEDED");
    }

    @Test
    void crossCustomerPaymentIsDeniedAndDoesNotCreatePayment() throws Exception {
        String owner = "payment-owner@example.com";
        String attacker = "payment-attacker@example.com";
        UUID orderId = pendingOrder(owner);
        user(attacker);

        createPayment(attacker, "payment-key-5", paymentJson(orderId, "pm_approved"))
                .andExpect(status().isForbidden());

        CustomerOrderRecord order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(idempotencyRepository.findAll()).isEmpty();
        assertThat(auditActions()).doesNotContain("PAYMENT_SUCCEEDED", "PAYMENT_FAILED");
    }

    private org.springframework.test.web.servlet.ResultActions createPayment(
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

    private UUID pendingOrder(String username) throws Exception {
        user(username);
        ProductModel product = product("Payment Product", 10);
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
                .firstName("Payment")
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

    private void assertBalancedPaymentLedger(PaymentRecord payment) {
        LedgerTransactionRecord transaction = ledgerTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        "PAYMENT",
                        payment.getId().toString(),
                        LedgerTransactionType.PAYMENT_CAPTURE
                )
                .orElseThrow();
        List<LedgerEntryRecord> entries = ledgerEntryRepository.findByTransactionId(transaction.getId());

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(LedgerEntryRecord::getDirection)
                .containsExactlyInAnyOrder(LedgerEntryDirection.DEBIT, LedgerEntryDirection.CREDIT);
        assertThat(total(entries, LedgerEntryDirection.DEBIT)).isEqualByComparingTo(payment.getAmount());
        assertThat(total(entries, LedgerEntryDirection.CREDIT)).isEqualByComparingTo(payment.getAmount());
        assertThat(entries)
                .extracting(LedgerEntryRecord::getCurrency)
                .containsOnly(payment.getCurrency());
    }

    private BigDecimal total(List<LedgerEntryRecord> entries, LedgerEntryDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(LedgerEntryRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
}
