package io.aigoodle.model.provider.builtin;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.RemoteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps Ollama's {@code /api/tags} response into the provider-neutral model catalog. */
final class OllamaRemoteModelCatalog {

    List<RemoteModel> fromResponse(Map<String, Object> responseBody) {
        if (responseBody == null || !(responseBody.get("models") instanceof List<?> entries)) {
            return List.of();
        }

        List<RemoteModel> models = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            RemoteModel model = toRemoteModel(entry);
            if (model != null) {
                models.add(model);
            }
        }
        return models;
    }

    private static RemoteModel toRemoteModel(Object entry) {
        if (!(entry instanceof Map<?, ?> modelData)) {
            return null;
        }
        Object configuredName = modelData.get("name");
        if (configuredName == null || String.valueOf(configuredName).isBlank()) {
            return null;
        }

        String modelId = String.valueOf(configuredName);
        ModelType inferredType = RemoteModel.inferModelType(modelId);
        return RemoteModel.builder()
                .modelId(modelId)
                .label(modelId)
                .modelType(inferredType)
                .ownedBy(OllamaModelProvider.NAME)
                .typeInferred(true)
                .build();
    }
}
