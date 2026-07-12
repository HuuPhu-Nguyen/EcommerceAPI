package com.phu.ecommerceapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app.security.oauth2")
public record OAuth2ResourceServerSecurityProperties(
        @NotBlank String requiredAudience,
        @NotBlank String resourceClientId,
        List<String> allowedAuthorizedParties
) {
    public OAuth2ResourceServerSecurityProperties {
        requiredAudience = normalizeText(requiredAudience);
        resourceClientId = normalizeText(resourceClientId);
        allowedAuthorizedParties = normalizeAllowedAuthorizedParties(allowedAuthorizedParties);
    }

    public boolean enforcesAuthorizedParty() {
        return !allowedAuthorizedParties.isEmpty();
    }

    private static List<String> normalizeAllowedAuthorizedParties(List<String> values) {
        if (values == null) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            for (String token : splitCommaSeparated(value)) {
                String authorizedParty = normalizeText(token);
                if (!authorizedParty.isBlank()) {
                    normalized.add(authorizedParty);
                }
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> splitCommaSeparated(String value) {
        if (value == null) {
            return List.of("");
        }

        String[] tokens = value.split(",");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            result.add(token);
        }
        return result;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
