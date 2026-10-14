package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A normalized model locator read from current, legacy, or Web API workflow shapes.
 */
record NodeModelReference(String provider, String modelName, String entityId, Map<String, Object> completionSettings) {

    static NodeModelReference from(NodeDef node) {
        Object configuredModel = node.get("model");
        if (configuredModel instanceof Map<?, ?> modelData) {
            String provider = firstText(modelData, "modelProvider", "providerName", "provider");
            String modelName = firstText(modelData, "modelName", "model");
            if (provider != null && modelName != null) {
                return new NodeModelReference(provider, modelName, null, settingsFrom(modelData));
            }
            NodeModelReference nestedId = fromModelId(text(modelData.get("modelId")), settingsFrom(modelData));
            if (nestedId != null) {
                return nestedId;
            }
        }

        String provider = text(node.get("modelProvider"));
        String modelName = text(node.get("modelName"));
        if (provider != null && modelName != null) {
            return new NodeModelReference(provider, modelName, null, Map.of());
        }
        return fromModelId(text(node.get("modelId")), Map.of());
    }

    boolean identifiesProviderModel() {
        return provider != null && modelName != null;
    }

    boolean identifiesEntity() {
        return entityId != null;
    }

    boolean hasCompletionSettings() {
        return !completionSettings.isEmpty();
    }

    private static NodeModelReference fromModelId(String modelId, Map<String, Object> completionSettings) {
        if (modelId == null) {
            return null;
        }
        String[] compositeParts = modelId.split("::", -1);
        if (compositeParts.length >= 2) {
            String provider = text(compositeParts[0]);
            String modelName = text(compositeParts[1]);
            if (provider != null && modelName != null) {
                return new NodeModelReference(provider, modelName, null, completionSettings);
            }
            return null;
        }
        return new NodeModelReference(null, null, modelId, completionSettings);
    }

    private static Map<String, Object> settingsFrom(Map<?, ?> modelData) {
        Object configuredSettings = modelData.get("completionParams");
        if (!(configuredSettings instanceof Map<?, ?> settings) || settings.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        settings.forEach((name, value) -> copy.put(String.valueOf(name), value));
        return Collections.unmodifiableMap(copy);
    }

    private static String firstText(Map<?, ?> values, String... names) {
        for (String name : names) {
            String value = text(values.get(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
