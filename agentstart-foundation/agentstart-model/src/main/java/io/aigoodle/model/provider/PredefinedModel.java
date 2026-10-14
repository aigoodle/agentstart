package io.aigoodle.model.provider;

import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

/**
 * A model a provider ships knowledge about out-of-the-box (label, type, features,
 * context window). Custom models registered by a user are not listed here.
 */
@Data
@Builder
public class PredefinedModel {

    private String model;
    private String label;
    private ModelType modelType;

    @Builder.Default
    private Set<ModelFeature> features = Set.of();
    /** Context window in tokens, when known. */
    private Integer contextLength;
    /** Output vector size for embedding models, when known. */
    private Integer dimensions;

    /**
     * Per-model parameter rule override. Null means "use whatever the provider
     * returns from {@link ModelProvider#defaultParameterRules}" — most presets can
     * rely on the default; set this only when a specific model has an unusual
     * range (e.g. a reasoner that doesn't accept temperature).
     */
    private List<ModelParameterRule> parameterRules;
}
