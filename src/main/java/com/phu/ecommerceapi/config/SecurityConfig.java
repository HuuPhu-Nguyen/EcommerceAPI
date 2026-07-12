package com.phu.ecommerceapi.config;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(OAuth2ResourceServerSecurityProperties.class)
public class SecurityConfig {

    private final OAuth2ResourceServerSecurityProperties securityProperties;

    public SecurityConfig(OAuth2ResourceServerSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/register").permitAll()
                                .requestMatchers(
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html"
                                ).permitAll()
                                .requestMatchers(HttpMethod.POST, "/payments/provider-webhooks/fake").permitAll()
                                .requestMatchers(HttpMethod.POST, "/payments/provider-webhooks/stripe").permitAll()
                                .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                ))
                .build();
    }

    @Bean
    public OAuth2AudienceAndAuthorizedPartyValidator oauth2AudienceAndAuthorizedPartyValidator() {
        return new OAuth2AudienceAndAuthorizedPartyValidator(securityProperties);
    }

    @Bean
    public JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            OAuth2AudienceAndAuthorizedPartyValidator audienceAndAuthorizedPartyValidator
    ) {
        OAuth2ResourceServerProperties.Jwt jwtProperties = resourceServerProperties.getJwt();
        String issuerUri = jwtProperties.getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalStateException("spring.security.oauth2.resourceserver.jwt.issuer-uri is required");
        }

        NimbusJwtDecoder decoder = jwtDecoder(jwtProperties, issuerUri);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceAndAuthorizedPartyValidator
        ));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);
            Set<GrantedAuthority> authorities = scopeAuthorities == null
                    ? new HashSet<>()
                    : new HashSet<>(scopeAuthorities);
            addRealmRoles(jwt, authorities);
            addResourceRoles(jwt, authorities);
            return authorities;
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    private void addRealmRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return;
        }

        addRoles(realmAccessMap.get("roles"), authorities);
    }

    private void addResourceRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resourceAccessMap)) {
            return;
        }

        Object resource = resourceAccessMap.get(securityProperties.resourceClientId());
        if (!(resource instanceof Map<?, ?> resourceMap)) {
            return;
        }

        addRoles(resourceMap.get("roles"), authorities);
    }

    private void addRoles(Object roles, Set<GrantedAuthority> authorities) {
        if (!(roles instanceof List<?> roleList)) {
            return;
        }

        roleList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> "ROLE_" + role.toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
    }

    private NimbusJwtDecoder jwtDecoder(OAuth2ResourceServerProperties.Jwt jwtProperties, String issuerUri) {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri()).build();
        }

        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}
