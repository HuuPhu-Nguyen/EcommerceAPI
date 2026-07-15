package com.phu.ecommerceapi.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DeploymentProfileGuard implements InitializingBean {

    private static final String LOCAL_PROFILE = "local";
    private static final String PROD_PROFILE = "prod";
    private static final String CONTAINERIZED_PROPERTY = "app.deployment.containerized";
    private static final String APP_ENVIRONMENT_PROPERTY = "app.environment";
    private static final String OAUTH_ALLOWED_AUTHORIZED_PARTIES_PROPERTY =
            "app.security.oauth2.allowed-authorized-parties";
    private static final String AUDIT_SIGNATURE_SECRET_PROPERTY = "app.audit.signature-secret";
    private static final String RATE_LIMIT_BACKEND_PROPERTY = "app.security.rate-limit.backend";
    private static final String IN_MEMORY_RATE_LIMIT_BACKEND = "in-memory";
    private static final Set<String> SUPPORTED_PROFILES = Set.of("local", "test", "prod");
    private static final Set<String> LOCAL_ENVIRONMENTS = Set.of("local", "test");
    private static final Set<String> RATE_LIMIT_BACKENDS = Set.of(IN_MEMORY_RATE_LIMIT_BACKEND, "gateway", "redis");

    private final Environment environment;

    public DeploymentProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> activeProfiles = activeProfiles();
        if (activeProfiles.isEmpty()) {
            throw invalidProfileConfiguration(
                    "SPRING_PROFILES_ACTIVE must be explicitly set to one of local,test,prod"
            );
        }

        Set<String> unsupportedProfiles = activeProfiles.stream()
                .filter(profile -> !SUPPORTED_PROFILES.contains(profile))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unsupportedProfiles.isEmpty()) {
            throw invalidProfileConfiguration(
                    "Unsupported Spring profile(s): " + String.join(",", unsupportedProfiles)
                            + ". SPRING_PROFILES_ACTIVE must be explicitly set to one of local,test,prod"
            );
        }

        if (activeProfiles.contains(PROD_PROFILE)) {
            requireProdRateLimitBackend();
            requireProdProperty(
                    OAUTH_ALLOWED_AUTHORIZED_PARTIES_PROPERTY,
                    "OAUTH2_ALLOWED_AUTHORIZED_PARTIES is required in prod"
            );
            requireProdProperty(
                    AUDIT_SIGNATURE_SECRET_PROPERTY,
                    "AUDIT_SIGNATURE_SECRET is required in prod"
            );
        } else {
            validateConfiguredRateLimitBackend();
        }

        if (!activeProfiles.contains(LOCAL_PROFILE)) {
            return;
        }

        if (isContainerized()) {
            throw invalidDeployment("containerized runtime marker is enabled");
        }

        String appEnvironment = normalize(environment.getProperty(APP_ENVIRONMENT_PROPERTY, ""));
        if (!LOCAL_ENVIRONMENTS.contains(appEnvironment)) {
            throw invalidDeployment("app.environment is set to " + appEnvironment);
        }
    }

    private Set<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return Arrays.stream(profiles)
                .map(DeploymentProfileGuard::normalize)
                .filter(profile -> !profile.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isContainerized() {
        return Boolean.parseBoolean(environment.getProperty(CONTAINERIZED_PROPERTY, "false"));
    }

    private void requireProdProperty(String propertyName, String failureMessage) {
        String value;
        try {
            value = environment.getProperty(propertyName, "");
        } catch (IllegalArgumentException exception) {
            throw invalidProfileConfiguration(failureMessage);
        }
        if (value == null || value.isBlank()) {
            throw invalidProfileConfiguration(failureMessage);
        }
    }

    private void requireProdRateLimitBackend() {
        String backend = configuredRateLimitBackend("RATE_LIMIT_BACKEND is required in prod");
        if (backend.isBlank()) {
            throw invalidProfileConfiguration("RATE_LIMIT_BACKEND is required in prod");
        }
        validateRateLimitBackend(backend);
        if (IN_MEMORY_RATE_LIMIT_BACKEND.equals(backend)) {
            throw invalidProfileConfiguration("RATE_LIMIT_BACKEND cannot be in-memory in prod");
        }
    }

    private void validateConfiguredRateLimitBackend() {
        validateRateLimitBackend(configuredRateLimitBackend("RATE_LIMIT_BACKEND must be one of in-memory,gateway,redis"));
    }

    private String configuredRateLimitBackend(String unresolvedPlaceholderMessage) {
        try {
            return normalize(environment.getProperty(RATE_LIMIT_BACKEND_PROPERTY, ""));
        } catch (IllegalArgumentException exception) {
            throw invalidProfileConfiguration(unresolvedPlaceholderMessage);
        }
    }

    private void validateRateLimitBackend(String backend) {
        if (backend.isBlank() || !RATE_LIMIT_BACKENDS.contains(backend)) {
            throw invalidProfileConfiguration("RATE_LIMIT_BACKEND must be one of in-memory,gateway,redis");
        }
    }

    private ApplicationContextException invalidDeployment(String reason) {
        return new ApplicationContextException(
                "Refusing to start with the local Spring profile in a deployment context because "
                        + reason
                        + ". Use SPRING_PROFILES_ACTIVE=prod for Docker or production deployments."
        );
    }

    private ApplicationContextException invalidProfileConfiguration(String reason) {
        return new ApplicationContextException(reason);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
