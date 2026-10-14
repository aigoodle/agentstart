package io.aigoodle.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the model module, bound from {@code spring-agent.model.*}.
 */
@ConfigurationProperties(prefix = "spring-agent.model")
public class GoodleModelProperties {

    /**
     * Secret used to encrypt credentials at rest. Override in production; a
     * development default is provided so the module works out of the box.
     */
    private String encryptionSecret = "spring-agent-start-dev-secret-change-me";

    /** Register the built-in OpenAI-compatible + Ollama provider presets. */
    private boolean registerBuiltinProviders = true;

    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    public void setEncryptionSecret(String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }

    public boolean isRegisterBuiltinProviders() {
        return registerBuiltinProviders;
    }

    public void setRegisterBuiltinProviders(boolean registerBuiltinProviders) {
        this.registerBuiltinProviders = registerBuiltinProviders;
    }
}
