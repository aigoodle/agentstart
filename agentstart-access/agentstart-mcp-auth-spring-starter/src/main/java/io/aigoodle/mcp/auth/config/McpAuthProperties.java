package io.aigoodle.mcp.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring-agent.mcp.auth")
public class McpAuthProperties {

    private boolean enabled = true;
    private List<String> contextKeys = new ArrayList<>(List.of("authorization", "Authorization"));
    private final Jwt jwt = new Jwt();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getContextKeys() {
        return contextKeys;
    }

    public void setContextKeys(List<String> contextKeys) {
        this.contextKeys = contextKeys;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public static class Jwt {
        private boolean enabled;
        private String secret;
        private String issuer;
        private String audience;
        private Duration clockSkew = Duration.ofSeconds(30);
        private String userIdClaim = "sub";
        private String usernameClaim = "preferred_username";
        private String tenantIdClaim = "tenant_id";
        private String appIdClaim = "app_id";
        private String rolesClaim = "roles";
        private String scopesClaim = "scope";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        public String getUserIdClaim() {
            return userIdClaim;
        }

        public void setUserIdClaim(String userIdClaim) {
            this.userIdClaim = userIdClaim;
        }

        public String getUsernameClaim() {
            return usernameClaim;
        }

        public void setUsernameClaim(String usernameClaim) {
            this.usernameClaim = usernameClaim;
        }

        public String getTenantIdClaim() {
            return tenantIdClaim;
        }

        public void setTenantIdClaim(String tenantIdClaim) {
            this.tenantIdClaim = tenantIdClaim;
        }

        public String getAppIdClaim() {
            return appIdClaim;
        }

        public void setAppIdClaim(String appIdClaim) {
            this.appIdClaim = appIdClaim;
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        public String getScopesClaim() {
            return scopesClaim;
        }

        public void setScopesClaim(String scopesClaim) {
            this.scopesClaim = scopesClaim;
        }
    }
}
