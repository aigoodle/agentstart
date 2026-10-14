package io.aigoodle.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecretEnvironmentPostProcessorTest {
    private final ProductionSecretEnvironmentPostProcessor guard = new ProductionSecretEnvironmentPostProcessor();

    @Test
    void developmentDefaultsRemainAvailableForTheStandaloneDemo() {
        MockEnvironment environment = new MockEnvironment();

        guard.postProcessEnvironment(environment, new SpringApplication());

        assertThat(ProductionSecretEnvironmentPostProcessor.guardEnabled(environment))
                .isFalse();
    }

    @Test
    void productionProfileRejectsDemoIdentitySecretsAndCorsBeforeContextStartup() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "production");
        environment.setActiveProfiles("production");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring-agent.demo.enabled")
                .hasMessageContaining("spring-agent.model.encryption-secret")
                .hasMessageContaining("spring-agent.channel.encryption-secret")
                .hasMessageContaining("spring-agent.web.allowed-origins");
    }

    @Test
    void productionAcceptsExplicitHostOwnedIdentityAndStrongSecrets() {
        MockEnvironment environment = secureEnvironment();

        assertThat(ProductionSecretEnvironmentPostProcessor.violations(environment))
                .isEmpty();
        guard.postProcessEnvironment(environment, new SpringApplication());
    }

    @Test
    void explicitGuardWorksWithoutAProductionProfileForDeploymentPipelines() {
        MockEnvironment environment = secureEnvironment();
        environment.setActiveProfiles();
        environment.setProperty(ProductionSecretEnvironmentPostProcessor.EXPLICIT_GUARD, "true");
        environment.setProperty("spring-agent.model.encryption-secret", "short");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .hasMessageContaining("model.encryption-secret");
    }

    private static MockEnvironment secureEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring-agent.demo.enabled", "false")
                .withProperty("spring-agent.demo.allow-debug", "false")
                .withProperty("spring-agent.web.allowed-origins", "https://agent.example.com")
                .withProperty("spring-agent.model.encryption-secret", "model-encryption-secret-32-bytes-long")
                .withProperty("spring-agent.connector.encryption-secret", "connector-encryption-secret-32-bytes-long")
                .withProperty("spring-agent.channel.encryption-secret", "channel-encryption-secret-32-bytes-long")
                .withProperty("spring.datasource.password", "database-password-from-secret-store");
        environment.setActiveProfiles("production");
        return environment;
    }

    @org.junit.jupiter.api.Test
    void bootDiscoversProductionGuardThroughCurrentFactoriesRegistration() {
        var application =
                new org.springframework.boot.SpringApplication(io.aigoodle.server.GoodleServerApplication.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> application.run(
                        "--spring.profiles.active=production",
                        "--spring-agent.demo.enabled=true",
                        "--spring-agent.server.production-guard.enabled=true",
                        "--spring.main.banner-mode=off"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start agentstart-server");
    }
}
