package io.aigoodle.plugin;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class PluginManifestsTest {
    PluginManifest load(String text) throws Exception {
        return PluginManifests.load(new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void yamlObjectSchemasAndLegacyJsonHaveSameRuntimeShape() throws Exception {
        var yaml = load(
                """
                id: test.plugin
                version: '1'
                configurationSchema: {type: object}
                actions:
                  - id: run
                    timeout: PT30S
                    inputSchema: {type: object, required: [prompt]}
                """);
        assertThat(yaml.configurationSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(yaml.actions().getFirst().timeout()).hasSeconds(30);
        var legacy = load(io.aigoodle.common.util.JsonUtils.toJson(yaml));
        assertThat(legacy).isEqualTo(yaml);
    }

    @Test
    void rejectsDuplicateYamlKeysAndEscapingResources() {
        assertThatThrownBy(() -> load("id: a\nid: b\nversion: '1'\nactions: [{id: run}]"))
                .isInstanceOf(org.yaml.snakeyaml.error.YAMLException.class);
        assertThatThrownBy(() -> load("id: a\nversion: '1'\nactions: [{id: run}]\nskills: [{path: '../secret'}]"))
                .hasMessageContaining("inside its directory");
        assertThatThrownBy(() -> load("!!javax.script.ScriptEngineManager {}"))
                .isInstanceOf(org.yaml.snakeyaml.error.YAMLException.class);
    }
}
