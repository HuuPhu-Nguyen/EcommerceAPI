package com.phu.ecommerceapi.audit.api;

import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRecord;
import com.phu.ecommerceapi.audit.infrastructure.AuditEventRepository;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.shared.api.RequestMetadata;
import com.phu.ecommerceapi.shared.api.RequestMetadataHolder;
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

import static com.phu.ecommerceapi.audit.AuditEventTestCleaner.clearAuditEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditEventReadApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditEventRecorder auditEventRecorder;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        clearAuditEvents(jdbcTemplate);
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @Test
    void auditRecorderCapturesRequestMetadataFromHttpRequest() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .header("X-Request-Id", "audit-request-1")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.5")
                        .header("User-Agent", "AuditTest/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Audited Product", "19.99", 5, true))
                        .with(adminJwt("product:write"))
                        .with(remoteAddress("198.51.100.20")))
                .andExpect(status().isOk());

        AuditEventRecord event = auditEventRepository.findAll().get(0);
        assertThat(event.getAction()).isEqualTo("PRODUCT_CREATED");
        assertThat(event.getRequestId()).isNotEqualTo("audit-request-1");
        assertThat(event.getExternalCorrelationId()).isEqualTo("audit-request-1");
        assertThat(event.getIpAddress()).isEqualTo("198.51.100.20");
        assertThat(event.getUserAgent()).isEqualTo("AuditTest/1.0");
        assertThat(event.getEventSignature()).hasSize(64);
    }

    @Test
    void auditReadApiReturnsPersistedExternalCorrelationId() throws Exception {
        RequestMetadataHolder.set(new RequestMetadata(
                "internal-request-1",
                "external-correlation-1",
                "198.51.100.20",
                "AuditTest/1.0"
        ));
        try {
            auditEventRecorder.record(new AuditEventCommand(
                    "customer@example.com",
                    "PII_ACCESSED",
                    "USER",
                    "42",
                    "safe=value"
            ));
        } finally {
            RequestMetadataHolder.clear();
        }

        mockMvc.perform(get("/audit/events")
                        .param("limit", "10")
                        .with(auditorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value("internal-request-1"))
                .andExpect(jsonPath("$[0].externalCorrelationId").value("external-correlation-1"))
                .andExpect(jsonPath("$[0].ipAddress").value("198.51.100.20"))
                .andExpect(jsonPath("$[0].eventSignature").isNotEmpty());
    }

    @Test
    void auditorCanReadMaskedAuditEvents() throws Exception {
        auditEventRecorder.record(new AuditEventCommand(
                "customer@example.com",
                "PII_ACCESSED",
                "USER",
                "42",
                "email=customer@example.com;token=secret-token;password=secret-password;safe=value"
        ));

        MvcResult result = mockMvc.perform(get("/audit/events")
                        .param("limit", "10")
                        .with(auditorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actorSubject").value("c***@example.com"))
                .andExpect(jsonPath("$[0].action").value("PII_ACCESSED"))
                .andExpect(jsonPath("$[0].resourceType").value("USER"))
                .andExpect(jsonPath("$[0].resourceId").value("42"))
                .andExpect(jsonPath("$[0].requestId").value("unknown"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("email=***");
        assertThat(responseBody).contains("token=***");
        assertThat(responseBody).contains("password=***");
        assertThat(responseBody).contains("safe=value");
        assertThat(responseBody).doesNotContain("customer@example.com");
        assertThat(responseBody).doesNotContain("secret-token");
        assertThat(responseBody).doesNotContain("secret-password");
    }

    @Test
    void customerCannotReadAuditEvents() throws Exception {
        auditEventRecorder.record(new AuditEventCommand(
                "customer-subject",
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                "payment-1",
                "amount=20.00 USD"
        ));

        mockMvc.perform(get("/audit/events")
                        .with(customerJwt()))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor adminJwt(String scope) {
        return jwt()
                .jwt(jwt -> jwt.subject("admin-subject"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_" + scope)
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

    private RequestPostProcessor remoteAddress(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    private String productJson(String name, String price, int stock, boolean active) {
        return """
                {
                  "name": "%s",
                  "price": %s,
                  "stock": %d,
                  "active": %s
                }
                """.formatted(name, price, stock, active);
    }
}
