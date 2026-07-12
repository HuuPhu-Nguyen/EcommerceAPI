package com.phu.ecommerceapi.audit.api;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.audit.application.AuditHashChainBackfillService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private AuditHashChainBackfillService backfillService;

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
        auditEventRepository.deleteAll();
        jdbcTemplate.update("UPDATE audit_hash_chain_state SET latest_hash = NULL WHERE id = 1");
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
        assertThat(events.get(1).getPreviousHash()).isEqualTo(events.get(0).getEventHash());
        assertThat(result.verified()).isTrue();
        assertThat(result.checkedEvents()).isEqualTo(2);
        assertThat(result.latestHash()).isEqualTo(events.get(1).getEventHash());
    }

    @Test
    void modifiedAuditRecordBreaksVerification() {
        auditEventRecorder.record(command("PAYMENT_SUCCEEDED", "PAYMENT", "payment-2", "amount=20.00 USD"));
        Long eventId = eventsByIdAsc().get(0).getId();

        jdbcTemplate.update(
                "UPDATE audit_event SET details = ? WHERE id = ?",
                "amount=999.00 USD",
                eventId
        );

        AuditHashVerificationResult result = verificationService.verify();

        assertThat(result.verified()).isFalse();
        assertThat(result.checkedEvents()).isEqualTo(1);
        assertThat(result.brokenEventId()).isEqualTo(eventId);
        assertThat(result.message()).isEqualTo("Audit event hash mismatch");
    }

    @Test
    void legacyAuditRowsAreSealedWhenChainStateIsEmpty() {
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
        );

        backfillService.initializeLegacyChain();

        List<AuditEventRecord> events = eventsByIdAsc();
        AuditHashVerificationResult result = verificationService.verify();
        String latestHash = jdbcTemplate.queryForObject(
                "SELECT latest_hash FROM audit_hash_chain_state WHERE id = 1",
                String.class
        );

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPreviousHash()).isNull();
        assertThat(events.get(0).getEventHash()).hasSize(64);
        assertThat(result.verified()).isTrue();
        assertThat(result.latestHash()).isEqualTo(events.get(0).getEventHash());
        assertThat(latestHash).isEqualTo(events.get(0).getEventHash());
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
