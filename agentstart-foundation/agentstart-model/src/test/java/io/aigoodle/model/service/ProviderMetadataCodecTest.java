package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.PredefinedModel;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderMetadataCodecTest {

    @Test
    void ignoresUnknownModelTypesDuringRollingUpgrades() {
        assertThat(ProviderMetadataCodec.modelTypes("[\"llm\",\"future_type\"]"))
                .containsExactly(ModelType.LLM);
    }

    @Test
    void convertsInMemoryModelUsingReadableDefaults() {
        PredefinedModel model = PredefinedModel.builder()
                .model("gpt-readable")
                .modelType(ModelType.LLM)
                .features(Set.of(ModelFeature.STREAM))
                .build();

        PredefinedModelEntity entity = ProviderMetadataCodec.toEntity("openai", model, 7, "system");

        assertThat(entity.getLabel()).isEqualTo("gpt-readable");
        assertThat(entity.getProviderName()).isEqualTo("openai");
        assertThat(entity.getFeatures()).contains("STREAM");
        assertThat(entity.getSortOrder()).isEqualTo(7);
    }

    @Test
    void patchChangesOnlyFieldsPresentInTheRequest() {
        ProviderDefinitionEntity definition = new ProviderDefinitionEntity();
        definition.setLabel("Original");
        definition.setEnabled(true);

        ProviderDefinitionPatch.apply(definition, Map.of("label", "Readable"));

        assertThat(definition.getLabel()).isEqualTo("Readable");
        assertThat(definition.getEnabled()).isTrue();
    }
}
