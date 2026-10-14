package io.aigoodle.model.video;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.service.*;
import java.util.Map;

/** Uses the same tenant credentials and enable switches as the model management UI. */
public final class VideoModelService {
    private final ModelService models;
    private final ModelProviderRegistry providers;
    private final ProviderDefinitionService definitions;
    private final ProviderModelSettingsService settings;

    public VideoModelService(
            ModelService models,
            ModelProviderRegistry providers,
            ProviderDefinitionService definitions,
            ProviderModelSettingsService settings) {
        this.models = models;
        this.providers = providers;
        this.definitions = definitions;
        this.settings = settings;
    }

    public ModelEndpoint resolve(String tenantId, Map<String, Object> selection) {
        String id = text(selection.get("modelId"));
        io.aigoodle.model.entity.ModelEntity entity;
        if (id != null) entity = models.require(tenantId, id);
        else {
            String provider = text(selection.get("providerName")), name = text(selection.get("modelName"));
            if (provider == null || name == null) throw new IllegalArgumentException("Select a configured video model");
            if (!providers.get(provider).supports(ModelType.VIDEO))
                throw new IllegalArgumentException("Provider has no video capability");
            var predefined = definitions.findPredefined(tenantId, provider, name, ModelType.VIDEO);
            if (predefined != null && !settings.isEnabled(tenantId, provider, name, ModelType.VIDEO))
                throw new IllegalArgumentException("Video model is disabled");
            // Only catalogue entries or already registered custom models may be selected.
            if (predefined == null) {
                entity = models.listByProvider(tenantId, provider).stream()
                        .filter(value -> value.getModelType() == ModelType.VIDEO && name.equals(value.getModelName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Video model is not registered"));
            } else entity = models.findOrMaterialize(tenantId, provider, name, ModelType.VIDEO);
        }
        if (entity.getModelType() != ModelType.VIDEO || !Boolean.TRUE.equals(entity.getEnabled()))
            throw new IllegalArgumentException("Select an enabled video model");
        var definition = definitions.findByName(tenantId, entity.getProviderName());
        if (definition != null && Boolean.FALSE.equals(definition.getEnabled()))
            throw new IllegalArgumentException("Video provider is disabled");
        if (definitions.findPredefined(tenantId, entity.getProviderName(), entity.getModelName(), ModelType.VIDEO)
                        != null
                && !settings.isEnabled(tenantId, entity.getProviderName(), entity.getModelName(), ModelType.VIDEO))
            throw new IllegalArgumentException("Video model is disabled");
        return models.resolveEndpoint(entity);
    }

    public VideoModel create(ModelEndpoint endpoint) {
        if (endpoint.getModelType() != ModelType.VIDEO) throw new IllegalArgumentException("Not a video model");
        return providers.get(endpoint.getProviderName()).createVideoModel(endpoint);
    }

    public Map<String, Object> capabilities(String tenantId, Map<String, Object> selection) {
        var endpoint = resolve(tenantId, selection);
        return Map.of(
                "modelId",
                endpoint.getId(),
                "providerName",
                endpoint.getProviderName(),
                "modelName",
                endpoint.getModelName(),
                "parameterSchema",
                create(endpoint).parameterSchema(),
                "operations",
                java.util.List.of("TEXT_TO_VIDEO"));
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
