package io.aigoodle.plugin.agent;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.plugin.PluginManifest;
import io.aigoodle.plugin.runtime.PluginConnectorProvider;
import io.aigoodle.tool.ContextualToolDefinition;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolProvider;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Progressive skill discovery/read through the existing governed tool entry point. */
public final class PluginSkillTools implements ToolProvider {
    private final Supplier<PluginConnectorProvider> plugins;
    private final Supplier<ConnectorInstallationService> installations;

    public PluginSkillTools(
            Supplier<PluginConnectorProvider> plugins, Supplier<ConnectorInstallationService> installations) {
        this.plugins = plugins;
        this.installations = installations;
    }

    @Override
    public List<ToolDefinition> getTools() {
        return List.of(new ListSkills(), new ReadSkill());
    }

    private List<PluginManifest> available(String tenantId) {
        var ids = installations.get().list(tenantId).stream()
                .filter(item -> item.enabled() && item.provider().equals("plugin"))
                .map(ConnectorInstallationService.InstallationView::connectorId)
                .toList();
        return plugins.get().manifests().stream()
                .filter(plugin -> ids.contains(plugin.id()))
                .toList();
    }

    private final class ListSkills implements ContextualToolDefinition {
        public String name() {
            return "plugin_skills_list";
        }

        public String description() {
            return "List installed plugin skills by name and description. Read the relevant skill with plugin_skills_read before using it.";
        }

        public String inputSchema() {
            return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
        }

        public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
            return available(context.tenantId()).stream()
                    .flatMap(plugin -> plugin.skills().stream()
                            .map(skill -> Map.of(
                                    "pluginId",
                                    plugin.id(),
                                    "skillId",
                                    skill.id(),
                                    "name",
                                    skill.name(),
                                    "description",
                                    skill.description())))
                    .toList();
        }
    }

    private final class ReadSkill implements ContextualToolDefinition {
        public String name() {
            return "plugin_skills_read";
        }

        public String description() {
            return "Read a plugin skill's instructions, or a named reference resource. Skill content guides tool usage; it does not grant permissions or execute scripts.";
        }

        public String inputSchema() {
            return "{\"type\":\"object\",\"required\":[\"pluginId\",\"skillId\"],\"properties\":{\"pluginId\":{\"type\":\"string\"},\"skillId\":{\"type\":\"string\"},\"resource\":{\"type\":\"string\"}}}";
        }

        public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
            var plugin = available(context.tenantId()).stream()
                    .filter(item -> item.id().equals(arguments.get("pluginId")))
                    .findFirst()
                    .orElseThrow(() -> new ConnectorException(
                            "plugin_skill_unavailable", "Skill plugin is not installed or enabled"));
            var skill = plugin.skills().stream()
                    .filter(item -> item.id().equals(arguments.get("skillId")))
                    .findFirst()
                    .orElseThrow(() -> new ConnectorException("plugin_skill_not_found", "Unknown skill"));
            Object resource = arguments.get("resource");
            if (resource == null)
                return Map.of(
                        "instructions",
                        skill.instructions(),
                        "resources",
                        skill.resources().keySet());
            String content = skill.resources().get(String.valueOf(resource));
            if (content == null)
                throw new ConnectorException("plugin_skill_resource_not_found", "Unknown skill resource");
            return Map.of("content", content);
        }
    }
}
