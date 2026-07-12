package com.phu.ecommerceapi.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class OAuth2AudienceAndAuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

    private final OAuth2ResourceServerSecurityProperties properties;

    public OAuth2AudienceAndAuthorizedPartyValidator(OAuth2ResourceServerSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences == null || !audiences.contains(properties.requiredAudience())) {
            return invalidToken("JWT audience does not include the required API audience");
        }

        if (properties.enforcesAuthorizedParty()) {
            String authorizedParty = token.getClaimAsString("azp");
            if (authorizedParty == null || !properties.allowedAuthorizedParties().contains(authorizedParty)) {
                return invalidToken("JWT authorized party is not allowed for this API");
            }
        }

        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalidToken(String description) {
        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
