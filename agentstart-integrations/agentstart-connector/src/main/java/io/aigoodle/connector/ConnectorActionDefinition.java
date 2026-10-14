package io.aigoodle.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** One callable operation contributed by a connector. */
public record ConnectorActionDefinition(
        String id,
        String name,
        String description,
        String inputSchema,
        String outputSchema,
        boolean idempotent,
        Duration timeout,
        ConnectorRiskLevel riskLevel,
        Map<String, Object> metadata) {

    public ConnectorActionDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("action id must not be blank");
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? name : description;
        inputSchema = inputSchema == null || inputSchema.isBlank()
                ? "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":true}"
                : inputSchema;
        riskLevel = riskLevel == null ? ConnectorRiskLevel.WRITE : riskLevel;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @JsonProperty(value = "capabilities", access = JsonProperty.Access.READ_ONLY)
    public List<ConnectorCapability> capabilities() {
        Object configured = metadata.get("capabilities");
        if (configured instanceof List<?> values) {
            List<ConnectorCapability> parsed = values.stream()
                    .map(String::valueOf)
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
            if (!parsed.isEmpty()) return parsed;
        }
        return List.of(ConnectorCapability.ACTION);
    }
}
