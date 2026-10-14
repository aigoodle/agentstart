package io.aigoodle.plugin.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring-agent.plugin")
public class PluginProperties {
    private String hostBaseUrl;
    private String hostSigningSecret;

    public String getHostBaseUrl() {
        return hostBaseUrl;
    }

    public void setHostBaseUrl(String value) {
        hostBaseUrl = value;
    }

    public String getHostSigningSecret() {
        return hostSigningSecret;
    }

    public void setHostSigningSecret(String value) {
        hostSigningSecret = value;
    }

    private Map<String, List<String>> grants = new LinkedHashMap<>();
    private List<Remote> remotes = new ArrayList<>();

    public Map<String, List<String>> getGrants() {
        return grants;
    }

    public void setGrants(Map<String, List<String>> grants) {
        this.grants = grants;
    }

    public List<Remote> getRemotes() {
        return remotes;
    }

    public void setRemotes(List<Remote> remotes) {
        this.remotes = remotes;
    }

    public static class Remote {
        private String manifest;
        private String endpoint;
        private String token;
        private Duration timeout = Duration.ofSeconds(60);

        public String getManifest() {
            return manifest;
        }

        public void setManifest(String manifest) {
            this.manifest = manifest;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
