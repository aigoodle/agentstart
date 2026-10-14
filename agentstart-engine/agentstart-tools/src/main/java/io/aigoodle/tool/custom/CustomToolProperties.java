package io.aigoodle.tool.custom;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring-agent.tools.custom")
public class CustomToolProperties {
    private String configFile = "./data/custom-tools.json";
    private String encryptionSecret = "spring-agent-start-custom-tool-demo-secret-change-me";

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    public void setEncryptionSecret(String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }
}
