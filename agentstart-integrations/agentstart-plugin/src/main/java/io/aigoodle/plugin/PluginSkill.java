package io.aigoodle.plugin;

import java.util.Map;

/** Portable skill content. Discovery exposes name/description; instructions are read on demand. */
public record PluginSkill(
        String id, String name, String description, String instructions, Map<String, String> resources) {
    public PluginSkill {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]*")) throw new IllegalArgumentException("Invalid skill id");
        if (name == null
                || name.isBlank()
                || description == null
                || description.isBlank()
                || instructions == null
                || instructions.isBlank())
            throw new IllegalArgumentException("Skill name, description and instructions are required");
        resources = resources == null ? Map.of() : Map.copyOf(resources);
        resources.keySet().forEach(path -> {
            if (path.startsWith("/") || path.contains("..") || path.contains("\\") || path.contains(":"))
                throw new IllegalArgumentException("Skill resources require relative paths");
        });
    }
}
