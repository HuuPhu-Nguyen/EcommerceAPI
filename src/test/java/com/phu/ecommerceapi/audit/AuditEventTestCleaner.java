package com.phu.ecommerceapi.audit;

import org.springframework.jdbc.core.JdbcTemplate;

public final class AuditEventTestCleaner {

    private AuditEventTestCleaner() {
    }

    public static void clearAuditEvents(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE audit_event RESTART IDENTITY");
        jdbcTemplate.update("UPDATE audit_hash_chain_state SET latest_hash = NULL WHERE id = 1");
    }
}
