package com.phu.ecommerceapi.shared.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email address is required");
        }

        value = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Email address is invalid");
        }
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }
}
