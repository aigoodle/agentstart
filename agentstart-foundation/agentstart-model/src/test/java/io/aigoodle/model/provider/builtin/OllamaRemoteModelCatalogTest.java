package io.aigoodle.model.provider.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.RemoteModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaRemoteModelCatalogTest {

    private final OllamaRemoteModelCatalog catalog = new OllamaRemoteModelCatalog();

    @Test
    void mapsInstalledModelsAndInfersTheirTypesInServerOrder() {
        Map<String, Object> response = Map.of(
                "models",
                List.of(Map.of("name", "qwen3:8b", "size", 5_000_000_000L), Map.of("name", "nomic-embed-text:latest")));

        List<RemoteModel> models = catalog.fromResponse(response);

        assertThat(models).extracting(RemoteModel::getModelId).containsExactly("qwen3:8b", "nomic-embed-text:latest");
        assertThat(models)
                .extracting(RemoteModel::getModelType)
                .containsExactly(ModelType.LLM, ModelType.TEXT_EMBEDDING);
        assertThat(models).allSatisfy(model -> {
            assertThat(model.getOwnedBy()).isEqualTo("ollama");
            assertThat(model.isTypeInferred()).isTrue();
        });
    }

    @Test
    void ignoresMalformedEntriesAndMissingCatalogs() {
        assertThat(catalog.fromResponse(null)).isEmpty();
        assertThat(catalog.fromResponse(Map.of())).isEmpty();
        assertThat(catalog.fromResponse(
                        Map.of("models", List.of("unexpected", Map.of("digest", "abc"), Map.of("name", " ")))))
                .isEmpty();
    }
}
