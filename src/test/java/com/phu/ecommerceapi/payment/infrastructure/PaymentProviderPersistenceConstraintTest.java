package com.phu.ecommerceapi.payment.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentProviderPersistenceConstraintTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE refund_record, payment_record, customer_order, cart_model, user_model "
                        + "RESTART IDENTITY CASCADE"
        );
    }

    @AfterEach
    void cleanUpAfter() {
        cleanUp();
    }

    @Test
    void databaseAllowsMultipleFailedAttemptsForOneOrder() {
        TestOrder order = insertOrder();

        insertPayment(order, "FAILED", "fake", "fake_failed_1", "payment:fake:failed-key-1");
        insertPayment(order, "FAILED", "stripe", "stripe_failed_1", "payment:stripe:failed-key-1");

        assertThat(paymentCount(order.orderId())).isEqualTo(2);
    }

    @Test
    void databaseBlocksDuplicateActiveOrUnknownOutcomeAttemptsForOneOrder() {
        TestOrder pendingOrder = insertOrder();
        insertPayment(pendingOrder, "PENDING", "fake", null, "payment:fake:pending-key-1");

        assertThatThrownBy(() -> insertPayment(
                pendingOrder,
                "PENDING",
                "stripe",
                null,
                "payment:stripe:pending-key-2"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestOrder timeoutOrder = insertOrder();
        insertPayment(timeoutOrder, "PROVIDER_TIMEOUT", "fake", null, "payment:fake:timeout-key-1");

        assertThatThrownBy(() -> insertPayment(
                timeoutOrder,
                "PENDING",
                "stripe",
                null,
                "payment:stripe:timeout-key-2"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseBlocksDuplicateSuccessfulCapturedPaymentForOneOrder() {
        TestOrder order = insertOrder();
        insertPayment(order, "SUCCEEDED", "fake", "fake_success_1", "payment:fake:success-key-1");

        assertThatThrownBy(() -> insertPayment(
                order,
                "SUCCEEDED",
                "stripe",
                "stripe_success_1",
                "payment:stripe:success-key-2"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseScopesProviderReferencesAndIdempotencyKeysByProvider() {
        insertPayment(insertOrder(), "FAILED", "fake", "shared_provider_id", "shared-provider-key");
        insertPayment(insertOrder(), "FAILED", "stripe", "shared_provider_id", "shared-provider-key");

        assertThatThrownBy(() -> insertPayment(
                insertOrder(),
                "FAILED",
                "fake",
                "shared_provider_id",
                "different-provider-key"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertPayment(
                insertOrder(),
                "FAILED",
                "fake",
                "different_provider_id",
                "shared-provider-key"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TestOrder insertOrder() {
        Long customerId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO user_model (
                            username,
                            email,
                            first_name,
                            last_name,
                            identity_subject
                        )
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "provider-constraints-" + UUID.randomUUID() + "@example.com",
                "provider-constraints@example.com",
                "Provider",
                "Customer",
                "provider-constraints-" + UUID.randomUUID()
        );
        Long cartId = jdbcTemplate.queryForObject("SELECT nextval('cart_model_seq')", Long.class);
        UUID orderId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        INSERT INTO cart_model (id, total, owner_id, currency)
                        VALUES (?, ?, ?, ?)
                        """,
                cartId,
                new BigDecimal("20.00"),
                customerId,
                "USD"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_order (
                            id,
                            customer_id,
                            cart_id,
                            status,
                            total_amount,
                            currency,
                            created_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                orderId,
                customerId,
                cartId,
                "PENDING_PAYMENT",
                new BigDecimal("20.00"),
                "USD",
                OffsetDateTime.now()
        );

        return new TestOrder(orderId, customerId);
    }

    private void insertPayment(
            TestOrder order,
            String status,
            String providerCode,
            String providerPaymentId,
            String providerIdempotencyKey
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime completedAt = "PENDING".equals(status) ? null : now;
        String failureCode = switch (status) {
            case "FAILED" -> "provider_failed";
            case "PROVIDER_TIMEOUT" -> "provider_timeout";
            default -> null;
        };

        jdbcTemplate.update(
                """
                        INSERT INTO payment_record (
                            id,
                            order_id,
                            customer_id,
                            amount,
                            currency,
                            status,
                            provider_code,
                            provider_idempotency_key,
                            provider_payment_id,
                            provider_status,
                            failure_code,
                            provider_message,
                            idempotency_key,
                            created_at,
                            completed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                UUID.randomUUID(),
                order.orderId(),
                order.customerId(),
                new BigDecimal("20.00"),
                "USD",
                status,
                providerCode,
                providerIdempotencyKey,
                providerPaymentId,
                providerStatus(status),
                failureCode,
                "constraint test payment",
                "app-" + UUID.randomUUID(),
                now,
                completedAt
        );
    }

    private String providerStatus(String status) {
        return switch (status) {
            case "SUCCEEDED" -> "SUCCEEDED";
            case "FAILED" -> "FAILED";
            case "PROVIDER_TIMEOUT" -> "TIMEOUT";
            default -> null;
        };
    }

    private int paymentCount(UUID orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_record WHERE order_id = ?",
                Integer.class,
                orderId
        );
        return count == null ? 0 : count;
    }

    private record TestOrder(UUID orderId, long customerId) {
    }
}
