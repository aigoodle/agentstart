package io.aigoodle.model.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.provider.PredefinedModel;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts persisted provider metadata to and from its domain representation. */
final class ProviderMetadataCodec {

    private ProviderMetadataCodec() {}

    static Set<ModelType> modelTypes(String json) {
        Set<ModelType> types = EnumSet.noneOf(ModelType.class);
        if (json == null || json.isBlank()) {
            return types;
        }
        for (String name : JsonUtils.parseList(json, String.class)) {
            addEnumIgnoreUnknown(types, ModelType.class, name, true);
        }
        return types;
    }

    static List<CredentialField> credentialSchema(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseList(json, CredentialField.class);
    }

    static Map<ModelType, List<ModelParameterRule>> parameterRules(String json) {
        Map<ModelType, List<ModelParameterRule>> rules = new EnumMap<>(ModelType.class);
        if (json == null || json.isBlank()) {
            return rules;
        }
        Map<String, List<ModelParameterRule>> storedRules =
                JsonUtils.parse(json, new TypeReference<Map<String, List<ModelParameterRule>>>() {});
        if (storedRules == null) {
            return rules;
        }
        storedRules.forEach((type, typeRules) -> {
            try {
                rules.put(ModelType.valueOf(type.toUpperCase(Locale.ROOT)), typeRules);
            } catch (IllegalArgumentException ignored) {
                // Unknown values are tolerated during rolling upgrades.
            }
        });
        return rules;
    }

    static List<ModelParameterRule> ruleList(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseList(json, ModelParameterRule.class);
    }

    static Set<ModelFeature> features(String json) {
        Set<ModelFeature> features = EnumSet.noneOf(ModelFeature.class);
        if (json == null || json.isBlank()) {
            return features;
        }
        for (String name : JsonUtils.parseList(json, String.class)) {
            addEnumIgnoreUnknown(features, ModelFeature.class, name, false);
        }
        return features;
    }

    static PredefinedModelEntity toEntity(String providerName, PredefinedModel model, int sortOrder, String tenantId) {
        PredefinedModelEntity entity = new PredefinedModelEntity();
        entity.setTenantId(tenantId);
        entity.setProviderName(providerName);
        entity.setModel(model.getModel());
        entity.setLabel(model.getLabel() == null ? model.getModel() : model.getLabel());
        entity.setModelType(model.getModelType());
        entity.setContextLength(model.getContextLength());
        entity.setDimensions(model.getDimensions());
        entity.setSortOrder(sortOrder);
        if (model.getFeatures() != null && !model.getFeatures().isEmpty()) {
            entity.setFeatures(JsonUtils.toJson(
                    model.getFeatures().stream().map(Enum::name).toList()));
        }
        if (model.getParameterRules() != null && !model.getParameterRules().isEmpty()) {
            entity.setParameterRules(JsonUtils.toJson(model.getParameterRules()));
        }
        return entity;
    }

    private static <E extends Enum<E>> void addEnumIgnoreUnknown(
            Set<E> values, Class<E> enumType, String name, boolean caseInsensitive) {
        try {
            String candidate = caseInsensitive ? name.toUpperCase(Locale.ROOT) : name;
            values.add(Enum.valueOf(enumType, candidate));
        } catch (IllegalArgumentException ignored) {
            // Unknown values are tolerated during rolling upgrades.
        }
    }
}
