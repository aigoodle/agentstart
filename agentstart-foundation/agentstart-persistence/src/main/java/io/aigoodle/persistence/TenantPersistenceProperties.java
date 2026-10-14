package io.aigoodle.persistence;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring-agent.persistence.tenant-guard")
public class TenantPersistenceProperties {
    private boolean enabled = true;
    private Set<String> additionalTables = new LinkedHashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getAdditionalTables() {
        return additionalTables;
    }

    public void setAdditionalTables(Set<String> additionalTables) {
        this.additionalTables =
                additionalTables == null ? new LinkedHashSet<>() : new LinkedHashSet<>(additionalTables);
    }

    public Set<String> protectedTables() {
        Set<String> protectedTables = new LinkedHashSet<>(TenantGuardTables.defaults());
        protectedTables.addAll(additionalTables);
        return protectedTables;
    }
}
