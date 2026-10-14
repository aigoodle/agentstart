package io.aigoodle.model.service;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import java.util.Map;
import java.util.function.Consumer;

/** Applies the fields supported by the provider-definition PATCH contract. */
final class ProviderDefinitionPatch {

    private ProviderDefinitionPatch() {}

    static void apply(ProviderDefinitionEntity definition, Map<String, Object> values) {
        setString(values, "label", definition::setLabel);
        setString(values, "description", definition::setDescription);
        setString(values, "icon", definition::setIcon);
        setString(values, "svgIcon", definition::setSvgIcon);
        setString(values, "defaultBaseUrl", definition::setDefaultBaseUrl);

        if (values.containsKey("sortOrder")) {
            definition.setSortOrder((Integer) values.get("sortOrder"));
        }
        if (values.containsKey("enabled")) {
            definition.setEnabled(asBoolean(values.get("enabled")));
        }
        if (values.containsKey("supportsRemoteModelListing")) {
            definition.setSupportsRemoteModelListing(asBoolean(values.get("supportsRemoteModelListing")));
        }

        setJson(values, "credentialSchema", definition::setCredentialSchema);
        setJson(values, "defaultParameterRules", definition::setDefaultParameterRules);
        setJson(values, "supportedModelTypes", definition::setSupportedModelTypes);
    }

    private static void setString(Map<String, Object> values, String field, Consumer<String> setter) {
        if (values.containsKey(field)) {
            Object value = values.get(field);
            setter.accept(value == null ? null : String.valueOf(value));
        }
    }

    private static void setJson(Map<String, Object> values, String field, Consumer<String> setter) {
        if (values.containsKey(field)) {
            setter.accept(JsonUtils.toJson(values.get(field)));
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
