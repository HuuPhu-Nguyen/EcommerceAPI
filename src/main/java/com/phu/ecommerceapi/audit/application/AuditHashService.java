package com.phu.ecommerceapi.audit.application;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Service
public class AuditHashService {

    public String hash(AuditHashPayload payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalize(payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String canonicalize(AuditHashPayload payload) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, payload.previousHash());
        append(canonical, payload.actorSubject());
        append(canonical, payload.action());
        append(canonical, payload.resourceType());
        append(canonical, payload.resourceId());
        append(canonical, payload.details());
        append(canonical, payload.requestId());
        append(canonical, payload.ipAddress());
        append(canonical, payload.userAgent());
        append(canonical, DateTimeFormatter.ISO_INSTANT.format(payload.createdAt()));
        return canonical.toString();
    }

    private void append(StringBuilder canonical, String value) {
        String normalized = value == null ? "" : value;
        canonical
                .append(normalized.length())
                .append(':')
                .append(normalized)
                .append('|');
    }
}
