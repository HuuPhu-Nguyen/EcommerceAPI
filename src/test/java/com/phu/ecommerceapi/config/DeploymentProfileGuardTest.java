package com.phu.ecommerceapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextException;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentProfileGuardTest {

    @Test
    void missingActiveProfileIsRejected() {
        MockEnvironment environment = environmentWithRuntimeDefaults();

        assertThatThrownBy(() -> new DeploymentProfileGuard(environment).afterPropertiesSet())
                .isInstanceOf(ApplicationContextException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE must be explicitly set");
    }

    @Test
    void unsupportedActiveProfileIsRejected() {
        MockEnvironment environment = environmentWithRuntimeDefaults();
        environment.setActiveProfiles("staging");

        assertThatThrownBy(() -> new DeploymentProfileGuard(environment).afterPropertiesSet())
                .isInstanceOf(ApplicationContextException.class)
                .hasMessageContaining("Unsupported Spring profile(s): staging")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE must be explicitly set");
    }

    @Test
    void containerizedRuntimeRejectsActiveLocalProfile() {
        MockEnvironment environment = environmentWithRuntimeDefaults();
        environment.setActiveProfiles("local");
        environment.setProperty("app.deployment.containerized", "true");

        assertThatThrownBy(() -> new DeploymentProfileGuard(environment).afterPropertiesSet())
                .isInstanceOf(ApplicationContextException.class)
                .hasMessageContaining("local Spring profile")
                .hasMessageContaining("containerized runtime marker");
    }

    @Test
    void containerizedRuntimeAllowsProdProfile() throws Exception {
        MockEnvironment environment = environmentWithRuntimeDefaults();
        environment.setActiveProfiles("prod");
        environment.setProperty("app.deployment.containerized", "true");
        environment.setProperty("app.environment", "prod");

        new DeploymentProfileGuard(environment).afterPropertiesSet();
    }

    @Test
    void deploymentEnvironmentRejectsLocalProfile() {
        MockEnvironment environment = environmentWithRuntimeDefaults();
        environment.setActiveProfiles("local");
        environment.setProperty("app.environment", "prod");

        assertThatThrownBy(() -> new DeploymentProfileGuard(environment).afterPropertiesSet())
                .isInstanceOf(ApplicationContextException.class)
                .hasMessageContaining("local Spring profile")
                .hasMessageContaining("app.environment is set to prod");
    }

    @Test
    void localDevelopmentProfileIsAllowedWithoutDeploymentMarkers() throws Exception {
        MockEnvironment environment = environmentWithRuntimeDefaults();
        environment.setActiveProfiles("local");

        new DeploymentProfileGuard(environment).afterPropertiesSet();
    }

    @Test
    void testProfileIsAllowedWithoutDeploymentMarkers() throws Exception {
        MockEnvironment environment = environmentWithRuntimeDefaults();
        environment.setActiveProfiles("test");
        environment.setProperty("app.environment", "test");

        new DeploymentProfileGuard(environment).afterPropertiesSet();
    }

    @Test
    void dockerfileDefaultsToProdAndMarksRuntimeAsContainerized() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile)
                .contains("ENV SPRING_PROFILES_ACTIVE=prod")
                .contains("ENV APP_ENVIRONMENT=prod")
                .contains("ENV APP_CONTAINERIZED=true");
    }

    private MockEnvironment environmentWithRuntimeDefaults() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.environment", "local");
        environment.setProperty("app.deployment.containerized", "false");
        return environment;
    }
}
