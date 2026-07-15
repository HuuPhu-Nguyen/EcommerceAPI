package com.phu.ecommerceapi.audit.api;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.audit.application.AuditHashPayload;
import com.phu.ecommerceapi.audit.application.AuditHashService;
import com.phu.ecommerceapi.audit.application.AuditHashVerificationResult;
import com.phu.ecommerceapi.audit.application.AuditHashVerificationService;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRecord;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.phu.ecommerceapi.audit.AuditEventTestCleaner.clearAuditEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditHashVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditEventRecorder auditEventRecorder;

    @Autowired
    private AuditHashVerificationService verificationService;

    @Autowired
    private AuditHashService auditHashService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        clearAuditData();
    }

    @AfterEach
    void cleanData() {
        clearAuditData();
    }

    private void clearAuditData() {
        clearAuditEvents(jdbcTemplate);
    }

    @Test
    void untamperedAuditChainVerifies() {
        auditEventRecorder.record(command("PAYMENT_SUCCEEDED", "PAYMENT", "payment-1", "amount=20.00 USD"));
        auditEventRecorder.record(command("REFUND_SUCCEEDED", "REFUND", "refund-1", "amount=20.00 USD"));

        List<AuditEventRecord> events = eventsByIdAsc();
        AuditHashVerificationResult result = verificationService.verify();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getPreviousHash()).isNull();
        assertThat(events.get(0).getEventHash()).hasSize(64);
        assertThat(events.get(0).getEventSignature()).hasSize(64);
        assertThat(events.get(1).getPreviousHash()).isEqualTo(events.get(0).getEventHash());
        assertThat(events.get(1).getEventSignature()).hasSize(64);
        assertThat(result.verified()).isTrue();
        assertThat(result.checkedEvents()).isEqualTo(2);
        assertThat(result.latestHash()).isEqualTo(events.get(1).getEventHash());
    }

    @Test
    void directAuditEventUpdateIsRejectedByDatabase() {
        auditEventRecorder.record(command("PAYMENT_SUCCEEDED", "PAYMENT", "payment-2", "amount=20.00 USD"));
        Long eventId = eventsByIdAsc().get(0).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE audit_event SET details = ? WHERE id = ?",
                        "amount=999.00 USD",
                        eventId
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit events are append-only");

        AuditHashVerificationResult result = verificationService.verify();

        assertThat(result.verified()).isTrue();
        assertThat(result.checkedEvents()).isEqualTo(1);
        assertThat(auditEventRepository.findById(eventId).orElseThrow().getDetails())
                .isEqualTo("amount=20.00 USD");
    }

    @Test
    void directAuditEventDeleteIsRejectedByDatabase() {
        auditEventRecorder.record(command("PAYMENT_SUCCEEDED", "PAYMENT", "payment-legacy", "amount=20.00 USD"));
        Long eventId = eventsByIdAsc().get(0).getId();

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_event WHERE id = ?", eventId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit events are append-only");

        assertThat(auditEventRepository.findById(eventId)).isPresent();
    }

    @Test
    void unhashedAuditRowsAreRejectedByDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO audit_event (
                            actor_subject,
                            action,
                            resource_type,
                            resource_id,
                            details,
                            request_id,
                            ip_address,
                            user_agent,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        "legacy-actor",
                        "LEGACY_EVENT",
                        "PAYMENT",
                        "legacy-payment",
                        "amount=20.00 USD",
                        "legacy-request",
                        "127.0.0.1",
                        "legacy-agent",
                        OffsetDateTime.parse("2026-07-06T08:00:00Z")
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit event hash is required");
    }

    @Test
    void unsignedAuditRowsAreRejectedByDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
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
                            event_hash
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        "legacy-actor",
                        "LEGACY_EVENT",
                        "PAYMENT",
                        "legacy-payment",
                        "amount=20.00 USD",
                        "legacy-request",
                        "127.0.0.1",
                        "legacy-agent",
                        OffsetDateTime.parse("2026-07-06T08:00:00Z"),
                        "1".repeat(64)
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit event signature is required");
    }

    @Test
    void verificationFailsWhenStoredEventHashDoesNotMatchPayload() {
        String wrongEventHash = "a".repeat(64);
        insertRawAuditEvent(
                null,
                wrongEventHash,
                auditHashService.sign(wrongEventHash),
                "payment-hash-mismatch",
                "amount=20.00 USD",
                Instant.parse("2026-07-06T08:00:00Z")
        );

        AuditHashVerificationResult result = verificationService.verify();

        assertThat(result.verified()).isFalse();
        assertThat(result.checkedEvents()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Audit event hash mismatch");
    }

    @Test
    void verificationFailsWhenStoredEventSignatureDoesNotMatchEventHash() {
        Instant createdAt = Instant.parse("2026-07-06T08:00:00Z");
        AuditHashPayload payload = rawPayload(
                null,
                "payment-signature-mismatch",
                "amount=20.00 USD",
                createdAt
        );
        String eventHash = auditHashService.hash(payload);
        insertRawAuditEvent(
                null,
                eventHash,
                "0".repeat(64),
                "payment-signature-mismatch",
                "amount=20.00 USD",
                createdAt
        );

        AuditHashVerificationResult result = verificationService.verify();

        assertThat(result.verified()).isFalse();
        assertThat(result.checkedEvents()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Audit event signature mismatch");
    }

    @Test
    void auditorCanVerifyHashChainAndCustomerCannot() throws Exception {
        auditEventRecorder.record(command("PAYMENT_SUCCEEDED", "PAYMENT", "payment-3", "amount=20.00 USD"));

        mockMvc.perform(get("/audit/events/verification")
                        .with(auditorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.checkedEvents").value(1))
                .andExpect(jsonPath("$.latestHash").isNotEmpty());

        mockMvc.perform(get("/audit/events/verification")
                        .with(customerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void repositoryFindsAuditEventsByIdKeysetPage() {
        auditEventRecorder.record(command("PAYMENT_SUCCEEDED", "PAYMENT", "payment-4", "amount=20.00 USD"));
        auditEventRecorder.record(command("REFUND_SUCCEEDED", "REFUND", "refund-4", "amount=20.00 USD"));
        List<AuditEventRecord> events = eventsByIdAsc();

        List<AuditEventRecord> page = auditEventRepository.findByIdGreaterThanOrderByIdAsc(
                events.get(0).getId(),
                PageRequest.of(0, 1)
        );

        assertThat(page)
                .extracting(AuditEventRecord::getId)
                .containsExactly(events.get(1).getId());
    }

    private AuditEventCommand command(String action, String resourceType, String resourceId, String details) {
        return new AuditEventCommand(
                "audit-actor",
                action,
                resourceType,
                resourceId,
                details
        );
    }

    private List<AuditEventRecord> eventsByIdAsc() {
        return auditEventRepository.findByIdGreaterThanOrderByIdAsc(0, PageRequest.of(0, 100));
    }

    private void insertRawAuditEvent(
            String previousHash,
            String eventHash,
            String eventSignature,
            String resourceId,
            String details,
            Instant createdAt
    ) {
        jdbcTemplate.update("""
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
                            event_hash,
                            event_signature
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "audit-actor",
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                resourceId,
                details,
                "request-1",
                "127.0.0.1",
                "test-agent",
                createdAt.atOffset(ZoneOffset.UTC),
                previousHash,
                eventHash,
                eventSignature
        );
    }

    private AuditHashPayload rawPayload(
            String previousHash,
            String resourceId,
            String details,
            Instant createdAt
    ) {
        return new AuditHashPayload(
                previousHash,
                "audit-actor",
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                resourceId,
                details,
                "request-1",
                "127.0.0.1",
                "test-agent",
                createdAt
        );
    }

    private RequestPostProcessor auditorJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject("auditor-subject"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_AUDITOR"),
                        new SimpleGrantedAuthority("SCOPE_audit:read")
                );
    }

    private RequestPostProcessor customerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject("customer-subject"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("SCOPE_audit:read")
                );
    }
}
