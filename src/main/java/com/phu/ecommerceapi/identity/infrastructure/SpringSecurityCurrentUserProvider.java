package com.phu.ecommerceapi.identity.infrastructure;

import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.CurrentUserProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            return Optional.empty();
        }

        return Optional.of(toCurrentUser(authentication, jwt));
    }

    @Override
    public CurrentUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authenticated user is required"));
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private CurrentUser toCurrentUser(Authentication authentication, Jwt jwt) {
        String subject = firstText(jwt.getSubject(), authentication.getName());
        String username = firstText(jwt.getClaimAsString("preferred_username"), subject);
        String email = jwt.getClaimAsString("email");

        return new CurrentUser(
                subject,
                username,
                email,
                authoritiesWithPrefix(authentication, ROLE_PREFIX),
                authoritiesWithPrefix(authentication, SCOPE_PREFIX)
        );
    }

    private Set<String> authoritiesWithPrefix(Authentication authentication, String prefix) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(prefix))
                .map(authority -> authority.substring(prefix.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
