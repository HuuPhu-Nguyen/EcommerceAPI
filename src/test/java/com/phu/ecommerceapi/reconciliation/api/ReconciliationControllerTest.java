package com.phu.ecommerceapi.reconciliation.api;

import com.phu.ecommerceapi.config.SecurityConfig;
import com.phu.ecommerceapi.identity.application.CurrentUserProvider;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReport;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReconciliationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused-jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/test"
})
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReconciliationService reconciliationService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void auditorCanReadReconciliationReport() throws Exception {
        ReconciliationReport report = new ReconciliationReport(
                true,
                Instant.parse("2026-07-06T08:00:00Z"),
                1,
                0,
                1,
                List.of()
        );
        when(reconciliationService.runReport()).thenReturn(report);

        mockMvc.perform(get("/reconciliation/report").with(auditorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.checkedPayments").value(1))
                .andExpect(jsonPath("$.checkedLedgerTransactions").value(1))
                .andExpect(jsonPath("$.issues").isEmpty());

        verify(reconciliationService).runReport();
    }

    @Test
    void customerCannotReadReconciliationReport() throws Exception {
        mockMvc.perform(get("/reconciliation/report").with(customerJwt()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reconciliationService);
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
