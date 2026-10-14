package io.aigoodle.tool.custom;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomToolManagerTest {
    @TempDir
    Path directory;

    @Test
    void persistsDefinitionsAndEncryptsHeaders() throws Exception {
        Path catalog = directory.resolve("custom-tools.json");
        CustomToolProperties properties = new CustomToolProperties();
        properties.setConfigFile(catalog.toString());
        properties.setEncryptionSecret("a-long-test-secret");
        CustomToolManager manager = new CustomToolManager(properties);
        CustomHttpToolSpec spec = new CustomHttpToolSpec();
        spec.setName("query_order");
        spec.setDescription("Queries an order");
        spec.setMethod("GET");
        spec.setUrl("https://example.test/orders/{id}");
        spec.setHeaders(Map.of("Authorization", "Bearer top-secret"));

        manager.upsert(spec);

        assertThat(manager.getTools()).extracting(tool -> tool.name()).containsExactly("query_order");
        assertThat(Files.readString(catalog)).doesNotContain("Bearer top-secret");
        CustomToolManager restored = new CustomToolManager(properties);
        assertThat(restored.getTools()).hasSize(1);
        assertThat(restored.specs().getFirst().getHeaders()).containsEntry("Authorization", "********");
    }
}
