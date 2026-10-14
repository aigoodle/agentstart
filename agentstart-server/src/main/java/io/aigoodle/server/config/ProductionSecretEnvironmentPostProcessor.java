package io.aigoodle.server.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start the standalone distribution with its documented demo security
 * defaults in a production profile. Embedded starters deliberately do not install
 * this policy: the host application remains the authority for identity and secrets.
 */
public final class ProductionSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String EXPLICIT_GUARD = "spring-agent.server.production-guard.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!guardEnabled(environment)) return;
        List<String> violations = violations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Refusing to start agentstart-server with unsafe production settings: "
                    + String.join(", ", violations));
        }
    }

    static boolean guardEnabled(ConfigurableEnvironment environment) {
        return environment.getProperty(EXPLICIT_GUARD, Boolean.class, false)
                || environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    static List<String> violations(ConfigurableEnvironment environment) {
        List<String> values = new ArrayList<>();
        requireFalse(environment, values, "spring-agent.demo.enabled", true, "spring-agent.demo.enabled must be false");
        requireFalse(
                environment,
                values,
                "spring-agent.demo.allow-debug",
                true,
                "spring-agent.demo.allow-debug must be false");
        rejectWildcard(environment, values, "spring-agent.web.allowed-origins");
        rejectDefaultOrWeak(environment, values, "spring-agent.model.encryption-secret", "demo-secret-change-me", 32);
        rejectDefaultOrWeak(
                environment, values, "spring-agent.connector.encryption-secret", "demo-secret-change-me", 32);
        rejectDefaultOrWeak(environment, values, "spring-agent.channel.encryption-secret", "demo-secret-change-me", 32);
        rejectDefault(environment, values, "spring.datasource.password", "ai123456");
        return List.copyOf(values);
    }

    private static void requireFalse(
            ConfigurableEnvironment environment,
            List<String> violations,
            String key,
            boolean defaultValue,
            String message) {
        if (environment.getProperty(key, Boolean.class, defaultValue)) violations.add(message);
    }

    private static void rejectWildcard(ConfigurableEnvironment environment, List<String> violations, String key) {
        String value = environment.getProperty(key, "*");
        if (value.lines().anyMatch(line -> line.trim().equals("*")) || value.contains("[*]")) {
            violations.add(key + " must not contain wildcard origin");
        }
    }

    private static void rejectDefault(
            ConfigurableEnvironment environment, List<String> violations, String key, String unsafeDefault) {
        String value = environment.getProperty(key, unsafeDefault);
        if (value == null || value.isBlank() || unsafeDefault.equals(value)) {
            violations.add(key + " must be overridden");
        }
    }

    private static void rejectDefaultOrWeak(
            ConfigurableEnvironment environment,
            List<String> violations,
            String key,
            String unsafeDefault,
            int minimumLength) {
        String value = environment.getProperty(key, unsafeDefault);
        if (value == null || value.isBlank() || unsafeDefault.equals(value) || value.length() < minimumLength) {
            violations.add(key + " must be overridden with at least " + minimumLength + " characters");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
