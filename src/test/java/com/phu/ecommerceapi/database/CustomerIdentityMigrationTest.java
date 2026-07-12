package com.phu.ecommerceapi.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CustomerIdentityMigrationTest {

    @Test
    void migrationFailsClearlyForDuplicateNormalizedUsernames() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbcTemplate = jdbcTemplate(postgres);

            flyway(postgres)
                    .target("19")
                    .load()
                    .migrate();

            insertUser(jdbcTemplate, "Customer@example.com", "customer-one@example.com", "subject-one");
            insertUser(jdbcTemplate, "customer@example.com", "customer-two@example.com", "subject-two");

            Throwable thrown = catchThrowable(() -> flyway(postgres).load().migrate());

            assertThat(thrown).isInstanceOf(FlywayException.class);
            assertThat(rootCause(thrown).getMessage())
                    .contains("Duplicate normalized username exists before customer identity uniqueness migration");
        }
    }

    @Test
    void migrationFailsClearlyForDuplicateNormalizedEmails() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbcTemplate = jdbcTemplate(postgres);

            flyway(postgres)
                    .target("19")
                    .load()
                    .migrate();

            insertUser(jdbcTemplate, "customer-one@example.com", "Customer@example.com", "subject-one");
            insertUser(jdbcTemplate, "customer-two@example.com", "customer@example.com", "subject-two");

            Throwable thrown = catchThrowable(() -> flyway(postgres).load().migrate());

            assertThat(thrown).isInstanceOf(FlywayException.class);
            assertThat(rootCause(thrown).getMessage())
                    .contains("Duplicate normalized email exists before customer identity uniqueness migration");
        }
    }

    private void insertUser(
            JdbcTemplate jdbcTemplate,
            String username,
            String email,
            String identitySubject
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_model (
                            username,
                            email,
                            identity_subject
                        )
                        VALUES (?, ?, ?)
                        """,
                username,
                email,
                identitySubject
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

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
