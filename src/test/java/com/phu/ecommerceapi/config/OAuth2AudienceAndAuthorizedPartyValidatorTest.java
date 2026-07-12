package com.phu.ecommerceapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AudienceAndAuthorizedPartyValidatorTest {

    @Test
    void validAudienceAndAuthorizedPartyPasses() {
        OAuth2TokenValidatorResult result = validator(List.of("ecommerce-web"))
                .validate(jwt(List.of("ecommerce-api"), "ecommerce-web"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void missingAudienceFails() {
        OAuth2TokenValidatorResult result = validator(List.of("ecommerce-web"))
                .validate(jwtWithoutAudience("ecommerce-web"));

        assertInvalidToken(result);
    }

    @Test
    void wrongAudienceFails() {
        OAuth2TokenValidatorResult result = validator(List.of("ecommerce-web"))
                .validate(jwt(List.of("other-api"), "ecommerce-web"));

        assertInvalidToken(result);
    }

    @Test
    void missingAuthorizedPartyFailsWhenAllowlistIsNotEmpty() {
        OAuth2TokenValidatorResult result = validator(List.of("ecommerce-web"))
                .validate(jwtWithoutAuthorizedParty(List.of("ecommerce-api")));

        assertInvalidToken(result);
    }

    @Test
    void wrongAuthorizedPartyFails() {
        OAuth2TokenValidatorResult result = validator(List.of("ecommerce-web"))
                .validate(jwt(List.of("ecommerce-api"), "other-web"));

        assertInvalidToken(result);
    }

    @Test
    void allowedAuthorizedPartyPassesWhenMultiplePartiesAreConfigured() {
        OAuth2TokenValidatorResult result = validator(List.of("admin-console", "ecommerce-web"))
                .validate(jwt(List.of("ecommerce-api"), "ecommerce-web"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void authorizedPartyIsNotEnforcedWhenAllowlistIsEmpty() {
        OAuth2TokenValidatorResult result = validator(List.of())
                .validate(jwtWithoutAuthorizedParty(List.of("ecommerce-api")));

        assertThat(result.hasErrors()).isFalse();
    }

    private OAuth2AudienceAndAuthorizedPartyValidator validator(List<String> allowedAuthorizedParties) {
        return new OAuth2AudienceAndAuthorizedPartyValidator(new OAuth2ResourceServerSecurityProperties(
                "ecommerce-api",
                "ecommerce-api",
                allowedAuthorizedParties
        ));
    }

    private Jwt jwt(List<String> audiences, String authorizedParty) {
        return jwtBuilder()
                .claim("aud", audiences)
                .claim("azp", authorizedParty)
                .build();
    }

    private Jwt jwtWithoutAudience(String authorizedParty) {
        return jwtBuilder()
                .claim("azp", authorizedParty)
                .build();
    }

    private Jwt jwtWithoutAuthorizedParty(List<String> audiences) {
        return jwtBuilder()
                .claim("aud", audiences)
                .build();
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("customer-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    private void assertInvalidToken(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .containsOnly(OAuth2ErrorCodes.INVALID_TOKEN);
    }
}
