package com.phu.ecommerceapi.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ActiveProfiles("test")
class PostgreSqlIntegrationTest {

    private static final String ROLLBACK_PRODUCT_NAME = "rollback-proof-product";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product_model WHERE name = ?", ROLLBACK_PRODUCT_NAME);
    }

    @Test
    void flywayMigrationsCreateBankingCoreSchema() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");

        assertThat(tableNames()).contains(
                "user_model",
                "product_model",
                "inventory",
                "customer_order",
                "payment_idempotency_record",
                "payment_record",
                "refund_record",
                "provider_webhook_event",
                "ledger_account",
                "ledger_transaction",
                "ledger_entry",
                "audit_event",
                "outbox_event",
                "reconciliation_run",
                "reconciliation_issue_record"
        );

        assertThat(columnDataType("product_model", "price")).isEqualTo("numeric");
        assertThat(columnDataType("product_model", "stock")).isEqualTo("integer");
        assertThat(columnDataType("product_model", "currency")).isEqualTo("character varying");
        assertThat(columnNames("payment_record"))
                .contains("provider_code", "provider_idempotency_key");
        assertThat(columnNames("refund_record"))
                .contains("provider_code", "provider_idempotency_key");
        assertThat(columnNames("payment_idempotency_record"))
                .contains(
                        "resource_type",
                        "resource_id",
                        "provider_code",
                        "provider_idempotency_key",
                        "in_progress_expires_at",
                        "last_recovery_attempt_at",
                        "recovery_status"
                );
        assertThat(columnNames("provider_webhook_event"))
                .contains(
                        "provider_event_created_at",
                        "provider_event_type",
                        "provider_object_id",
                        "provider_object_type"
                );
        assertThat(indexNames("payment_idempotency_record"))
                .contains("idx_payment_idempotency_recovery");
        assertThat(indexNames("provider_webhook_event"))
                .contains(
                        "idx_provider_webhook_event_provider_created_at",
                        "idx_provider_webhook_event_provider_object"
                );
        assertThat(indexNames("outbox_event"))
                .contains("idx_outbox_event_processing_locked_at");
        assertThat(indexNames("reconciliation_run"))
                .contains("idx_reconciliation_run_status_completed_at");
        assertThat(indexNames("reconciliation_issue_record"))
                .contains("idx_reconciliation_issue_record_run_id_id");
        assertThat(indexNames("user_model"))
                .contains(
                        "ux_user_model_identity_subject",
                        "ux_user_model_username_lower",
                        "ux_user_model_email_lower"
                );
    }

    @Test
    void databaseTransactionRollsBackPartialPersistence() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                            INSERT INTO product_model (name, price, stock, active, currency)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    ROLLBACK_PRODUCT_NAME,
                    new BigDecimal("19.99"),
                    3,
                    true,
                    "USD"
            );
            throw new IllegalStateException("force rollback");
        }));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(productCount(ROLLBACK_PRODUCT_NAME)).isZero();
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList(
                """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                        """,
                String.class
        );
    }

    private String columnDataType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                        """,
                String.class,
                tableName,
                columnName
        );
    }

    private List<String> columnNames(String tableName) {
        return jdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                String.class,
                tableName
        );
    }

    private List<String> indexNames(String tableName) {
        return jdbcTemplate.queryForList(
                """
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND tablename = ?
                        """,
                String.class,
                tableName
        );
    }

    private int productCount(String productName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_model WHERE name = ?",
                Integer.class,
                productName
        );
        return count == null ? 0 : count;
    }
}
