package io.aigoodle.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Provider-neutral catalog representation used by UI, agents and workflows. */
public record ConnectorDefinition(
        ConnectorKey key,
        String name,
        String description,
        String version,
        ConnectorSource source,
        String icon,
        String category,
        String configurationSchema,
        List<ConnectorActionDefinition> actions,
        ConnectorTrustLevel trustLevel,
        String license,
        Map<String, Object> metadata) {

    public ConnectorDefinition {
        if (key == null) throw new IllegalArgumentException("key is required");
        name = name == null || name.isBlank() ? key.connectorId() : name;
        description = description == null ? name : description;
        version = version == null || version.isBlank() ? "unknown" : version;
        source = source == null ? ConnectorSource.REMOTE : source;
        actions = actions == null ? List.of() : List.copyOf(actions);
        trustLevel = trustLevel == null ? ConnectorTrustLevel.UNTRUSTED : trustLevel;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ConnectorActionDefinition action(String actionId) {
        return actions.stream()
                .filter(action -> action.id().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new ConnectorException(
                        "connector_action_not_found",
                        "No action '" + actionId + "' in connector " + key.externalForm()));
    }

    /**
     * Capability is deliberately separate from riskLevel. Providers declare it in
     * metadata.capabilities; legacy connectors remain ordinary callable Actions.
     */
    @JsonProperty("capabilities")
    public List<ConnectorCapability> capabilities() {
        Object configured = metadata.get("capabilities");
        if (!(configured instanceof List<?> values) || values.isEmpty()) return List.of(ConnectorCapability.ACTION);
        List<ConnectorCapability> parsed = values.stream()
                .map(String::valueOf)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(value -> {
                    try {
                        return ConnectorCapability.valueOf(value);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return parsed.isEmpty() ? List.of(ConnectorCapability.ACTION) : parsed;
    }
}
