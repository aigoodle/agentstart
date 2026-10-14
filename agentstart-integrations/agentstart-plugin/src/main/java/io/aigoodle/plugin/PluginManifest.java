package io.aigoodle.plugin;

import io.aigoodle.connector.ConnectorActionDefinition;
import java.util.List;
import java.util.Map;

/** Language-neutral descriptor; contains no deployment addresses or secrets. */
public record PluginManifest(
        String id,
        String version,
        String name,
        String description,
        String icon,
        String category,
        String configurationSchema,
        Map<String, Object> configurationUiSchema,
        List<ConnectorActionDefinition> actions,
        List<String> requestedCapabilities,
        List<PluginSkill> skills) {
    public PluginManifest(
            String id,
            String version,
            String name,
            String description,
            String icon,
            String category,
            String configurationSchema,
            Map<String, Object> configurationUiSchema,
            List<ConnectorActionDefinition> actions,
            List<String> requestedCapabilities) {
        this(
                id,
                version,
                name,
                description,
                icon,
                category,
                configurationSchema,
                configurationUiSchema,
                actions,
                requestedCapabilities,
                List.of());
    }

    public PluginManifest {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]*"))
            throw new IllegalArgumentException("Plugin id must be a lowercase namespaced identifier");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Plugin version is required");
        name = name == null || name.isBlank() ? id : name;
        configurationUiSchema = configurationUiSchema == null ? Map.of() : Map.copyOf(configurationUiSchema);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.isEmpty()) throw new IllegalArgumentException("Plugin must declare at least one action");
        if (actions.stream().map(ConnectorActionDefinition::id).distinct().count() != actions.size())
            throw new IllegalArgumentException("Duplicate plugin action id");
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
        skills = skills == null ? List.of() : List.copyOf(skills);
        if (skills.stream().map(PluginSkill::id).distinct().count() != skills.size())
            throw new IllegalArgumentException("Duplicate plugin skill id");
    }
}
