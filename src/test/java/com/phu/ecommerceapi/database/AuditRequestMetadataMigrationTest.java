package com.phu.ecommerceapi.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestMetadataMigrationTest {

    @Test
    void migrationAddsNullableExternalCorrelationIdWithoutRewritingRequestIds() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbcTemplate = jdbcTemplate(postgres);

            flyway(postgres)
                    .target("22")
                    .load()
                    .migrate();

            Long auditEventId = insertLegacyAuditEvent(jdbcTemplate);

            flyway(postgres)
                    .load()
                    .migrate();

            Map<String, Object> auditEvent = jdbcTemplate.queryForMap(
                    """
                            SELECT request_id, external_correlation_id
                            FROM audit_event
                            WHERE id = ?
                            """,
                    auditEventId
            );

            assertThat(auditEvent)
                    .containsEntry("request_id", "internal-request-before-v23")
                    .containsEntry("external_correlation_id", null);
            assertThat(columnNames(jdbcTemplate)).contains("external_correlation_id");
            assertThat(indexNames(jdbcTemplate)).contains("idx_audit_event_external_correlation_id");
        }
    }

    private Long insertLegacyAuditEvent(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO audit_event (
                            actor_subject,
                            action,
                            resource_type,
                            resource_id,
                            details,
                            request_id,
                            ip_address,
                            user_agent,
                            created_at,
                            previous_hash,
                            event_hash
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "customer-subject",
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                "payment-1",
                "amount=20.00 USD",
                "internal-request-before-v23",
                "198.51.100.10",
                "migration-test",
                OffsetDateTime.now(),
                null,
                null
        );
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

    private Iterable<String> columnNames(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'audit_event'
                        """,
                String.class
        );
    }

    private Iterable<String> indexNames(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                """
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND tablename = 'audit_event'
                        """,
                String.class
        );
    }
}
