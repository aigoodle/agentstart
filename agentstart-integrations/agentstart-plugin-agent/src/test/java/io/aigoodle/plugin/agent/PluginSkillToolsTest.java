package io.aigoodle.plugin.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.aigoodle.connector.ConnectorActionDefinition;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.plugin.*;
import io.aigoodle.plugin.host.PluginHostFactory;
import io.aigoodle.plugin.runtime.PluginConnectorProvider;
import io.aigoodle.tool.ContextualToolDefinition;
import io.aigoodle.tool.execution.ToolExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginSkillToolsTest {
    @Test
    void progressivelyExposesOnlySkillsFromEnabledTenantPlugins() {
        var skill = new PluginSkill(
                "video-brief",
                "Video brief",
                "Prepare video scripts",
                "Internal instructions",
                Map.of("references/style.md", "Style guide"));
        Plugin plugin = new Plugin() {
            public PluginManifest manifest() {
                return new PluginManifest(
                        "video",
                        "1",
                        "Video",
                        "Video",
                        null,
                        null,
                        "{}",
                        Map.of(),
                        List.of(new ConnectorActionDefinition(
                                "prepare", null, null, null, null, true, null, null, Map.of())),
                        List.of(),
                        List.of(skill));
            }

            public PluginResult execute(PluginInvocation request, PluginContext context) {
                return PluginResult.success("unused");
            }
        };
        var provider = new PluginConnectorProvider(
                List.of(plugin), request -> null, new PluginHostFactory(List.of(), Map.of()));
        var installations = mock(ConnectorInstallationService.class);
        when(installations.list("tenant-a"))
                .thenReturn(List.of(new ConnectorInstallationService.InstallationView(
                        "i", "tenant-a", "plugin", "video", "1", "NATIVE", true, "REVIEWED")));
        when(installations.list("tenant-b")).thenReturn(List.of());
        var tools = new PluginSkillTools(() -> provider, () -> installations).getTools();
        var context = new ToolExecutionContext("e", "tenant-a", "u", null, Map.of());
        Object index = ((ContextualToolDefinition) tools.getFirst()).execute(Map.of(), context);
        assertThat(index.toString()).contains("Video brief").doesNotContain("Internal instructions");
        var reader = (ContextualToolDefinition) tools.get(1);
        assertThat(reader.execute(Map.of("pluginId", "video", "skillId", "video-brief"), context)
                        .toString())
                .contains("Internal instructions");
        assertThatThrownBy(() -> reader.execute(
                        Map.of("pluginId", "video", "skillId", "video-brief"),
                        new ToolExecutionContext("e", "tenant-b", "u", null, Map.of())))
                .hasMessageContaining("not installed");
        assertThatThrownBy(() -> reader.execute(
                        Map.of("pluginId", "video", "skillId", "video-brief", "resource", "../secret"), context))
                .hasMessageContaining("Unknown skill resource");
    }
}
