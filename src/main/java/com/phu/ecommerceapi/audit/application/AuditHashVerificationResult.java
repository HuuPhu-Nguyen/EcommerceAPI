package com.phu.ecommerceapi.audit.application;

public record AuditHashVerificationResult(
        boolean verified,
        long checkedEvents,
        Long brokenEventId,
        String latestHash,
        String message
) {

    public static AuditHashVerificationResult verified(long checkedEvents, String latestHash) {
        return new AuditHashVerificationResult(true, checkedEvents, null, latestHash, "Audit hash chain is valid");
    }

    public static AuditHashVerificationResult broken(long checkedEvents, Long brokenEventId, String message) {
        return new AuditHashVerificationResult(false, checkedEvents, brokenEventId, null, message);
    }
}
