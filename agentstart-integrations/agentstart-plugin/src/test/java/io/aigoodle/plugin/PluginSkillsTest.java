package io.aigoodle.plugin;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class PluginSkillsTest {
    @Test
    void readsYamlMetadataAndKeepsInstructionsSeparate() throws Exception {
        var resource = new ByteArrayResource(
                "---\nname: product-video\ndescription: Write product scripts\n---\nRead product data first."
                        .getBytes(StandardCharsets.UTF_8));
        var skill = PluginSkills.load(resource);
        assertThat(skill.id()).isEqualTo("product-video");
        assertThat(skill.instructions()).isEqualTo("Read product data first.");
        assertThatThrownBy(() -> PluginSkills.load(resource, "../secret")).hasMessageContaining("inside");
    }

    @Test
    void rejectsExecutableYamlTags() {
        var resource = new ByteArrayResource(
                "---\n!!java.net.URL [https://example.com]\n---\nBody".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> PluginSkills.load(resource)).isInstanceOf(RuntimeException.class);
    }
}
