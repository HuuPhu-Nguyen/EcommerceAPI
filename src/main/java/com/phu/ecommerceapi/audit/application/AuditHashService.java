package com.phu.ecommerceapi.audit.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class AuditHashService {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final String signatureSecret;

    public AuditHashService(@Value("${app.audit.signature-secret:}") String signatureSecret) {
        this.signatureSecret = normalize(signatureSecret);
    }

    public String hash(AuditHashPayload payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalize(payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String sign(String eventHash) {
        String normalizedHash = normalize(eventHash);
        if (normalizedHash.isBlank()) {
            throw new IllegalArgumentException("eventHash is required for audit signature");
        }
        if (signatureSecret.isBlank()) {
            throw new IllegalStateException("app.audit.signature-secret is required for audit signatures");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return HexFormat.of().formatHex(mac.doFinal(normalizedHash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    public boolean signatureMatches(String eventHash, String eventSignature) {
        String normalizedSignature = normalize(eventSignature);
        if (normalizedSignature.isBlank()) {
            return false;
        }
        byte[] expected = sign(eventHash).getBytes(StandardCharsets.UTF_8);
        byte[] actual = normalizedSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
