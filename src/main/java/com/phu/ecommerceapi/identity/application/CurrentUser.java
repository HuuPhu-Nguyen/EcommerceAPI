package com.phu.ecommerceapi.identity.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(
        String subject,
        String username,
        String email,
        Set<String> roles,
        Set<String> scopes
) {

    public CurrentUser {
        subject = requireText(subject, "subject");
        username = blankToNull(username);
        email = blankToNull(email);
        roles = normalize(roles);
        scopes = normalize(scopes);
    }

    public boolean hasRole(String role) {
        return roles.contains(normalizeValue(role));
    }

    public boolean hasScope(String scope) {
        return scopes.contains(normalizeValue(scope));
    }

    public boolean hasSubject(String identitySubject) {
        String normalizedSubject = blankToNull(identitySubject);
        return normalizedSubject != null && subject.equals(normalizedSubject);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .map(CurrentUser::normalizeValue)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
