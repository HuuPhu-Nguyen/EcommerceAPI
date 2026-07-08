package com.phu.ecommerceapi.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderMetadataMigrationTest {

    @Test
    void providerMetadataMigrationBackfillsLegacyFakePaymentAndRefundRows() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbcTemplate = jdbcTemplate(postgres);

            flyway(postgres)
                    .target("16")
                    .load()
                    .migrate();

            LegacyRows legacyRows = insertLegacyPaymentAndRefundRows(jdbcTemplate);

            flyway(postgres)
                    .load()
                    .migrate();

            Map<String, Object> payment = jdbcTemplate.queryForMap(
                    """
                            SELECT provider_code, provider_idempotency_key
                            FROM payment_record
                            WHERE id = ?
                            """,
                    legacyRows.paymentId()
            );
            Map<String, Object> refund = jdbcTemplate.queryForMap(
                    """
                            SELECT provider_code, provider_idempotency_key
                            FROM refund_record
                            WHERE id = ?
                            """,
                    legacyRows.refundId()
            );

            assertThat(payment)
                    .containsEntry("provider_code", "fake")
                    .containsEntry(
                            "provider_idempotency_key",
                            "payment:fake:%d:%s:legacy-payment-key".formatted(
                                    legacyRows.customerId(),
                                    legacyRows.orderId()
                            )
                    );
            assertThat(refund)
                    .containsEntry("provider_code", "fake")
                    .containsEntry(
                            "provider_idempotency_key",
                            "refund:fake:%d:%s:legacy-refund-key".formatted(
                                    legacyRows.customerId(),
                                    legacyRows.paymentId()
                            )
                    );
        }
    }

    private LegacyRows insertLegacyPaymentAndRefundRows(JdbcTemplate jdbcTemplate) {
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
                "legacy-provider@example.com",
                "legacy-provider@example.com",
                "Legacy",
                "Customer",
                "legacy-provider@example.com"
        );
        Long cartId = jdbcTemplate.queryForObject("SELECT nextval('cart_model_seq')", Long.class);
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

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
                "PAID",
                new BigDecimal("20.00"),
                "USD",
                now
        );
        jdbcTemplate.update(
                """
                        INSERT INTO payment_record (
                            id,
                            order_id,
                            customer_id,
                            amount,
                            currency,
                            status,
                            provider_payment_id,
                            provider_status,
                            provider_message,
                            idempotency_key,
                            created_at,
                            completed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                paymentId,
                orderId,
                customerId,
                new BigDecimal("20.00"),
                "USD",
                "SUCCEEDED",
                "fake_legacy_payment",
                "SUCCEEDED",
                "legacy payment",
                "legacy-payment-key",
                now,
                now
        );
        jdbcTemplate.update(
                """
                        INSERT INTO refund_record (
                            id,
                            payment_id,
                            order_id,
                            customer_id,
                            amount,
                            currency,
                            status,
                            provider_refund_id,
                            provider_status,
                            provider_message,
                            idempotency_key,
                            reason,
                            created_at,
                            completed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                refundId,
                paymentId,
                orderId,
                customerId,
                new BigDecimal("20.00"),
                "USD",
                "SUCCEEDED",
                "fake_legacy_refund",
                "SUCCEEDED",
                "legacy refund",
                "legacy-refund-key",
                "customer_request",
                now,
                now
        );

        return new LegacyRows(customerId, orderId, paymentId, refundId);
    }

    private FluentConfiguration flyway(PostgreSQLContainer<?> postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
    }

    private JdbcTemplate jdbcTemplate(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        return new JdbcTemplate(dataSource);
    }

    private record LegacyRows(Long customerId, UUID orderId, UUID paymentId, UUID refundId) {
    }
}
