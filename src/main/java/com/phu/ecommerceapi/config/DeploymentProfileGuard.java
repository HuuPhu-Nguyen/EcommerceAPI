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
    private static final String CONTAINERIZED_PROPERTY = "app.deployment.containerized";
    private static final String APP_ENVIRONMENT_PROPERTY = "app.environment";
    private static final Set<String> LOCAL_ENVIRONMENTS = Set.of("", "local", "test");

    private final Environment environment;

    public DeploymentProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> effectiveProfiles = effectiveProfiles();
        if (!effectiveProfiles.contains(LOCAL_PROFILE)) {
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

    private Set<String> effectiveProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }

        return Arrays.stream(profiles)
                .map(DeploymentProfileGuard::normalize)
                .filter(profile -> !profile.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isContainerized() {
        return Boolean.parseBoolean(environment.getProperty(CONTAINERIZED_PROPERTY, "false"));
    }

    private ApplicationContextException invalidDeployment(String reason) {
        return new ApplicationContextException(
                "Refusing to start with the local Spring profile in a deployment context because "
                        + reason
                        + ". Use SPRING_PROFILES_ACTIVE=prod for Docker or production deployments."
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
