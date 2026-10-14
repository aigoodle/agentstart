package io.aigoodle.model.provider.builtin;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.provider.RemoteModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps an OpenAI-compatible models response into the provider-neutral catalog. */
final class RemoteModelCatalogMapper {

    private final Map<String, PredefinedModel> predefinedModelsById;

    RemoteModelCatalogMapper(List<PredefinedModel> predefinedModels) {
        this.predefinedModelsById = new HashMap<>();
        predefinedModels.forEach(model -> predefinedModelsById.put(model.getModel(), model));
    }

    List<RemoteModel> fromResponse(Map<String, Object> responseBody) {
        if (responseBody == null || !(responseBody.get("data") instanceof List<?> entries)) {
            return List.of();
        }

        List<RemoteModel> models = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> modelData) {
                RemoteModel model = toRemoteModel(modelData);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        return models;
    }

    private RemoteModel toRemoteModel(Map<?, ?> modelData) {
        String modelId = asString(modelData.get("id"));
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        String owner = asString(modelData.get("owned_by"));
        PredefinedModel predefinedModel = predefinedModelsById.get(modelId);
        if (predefinedModel != null) {
            RemoteModel remoteModel = RemoteModel.fromPredefined(predefinedModel);
            remoteModel.setOwnedBy(owner);
            return remoteModel;
        }

        ModelType inferredType = RemoteModel.inferModelType(modelId);
        return RemoteModel.builder()
                .modelId(modelId)
                .label(modelId)
                .modelType(inferredType)
                .ownedBy(owner)
                .typeInferred(true)
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
