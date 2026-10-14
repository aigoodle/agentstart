package io.aigoodle.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.plugin.PluginManifest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compile once at discovery; validate resolved values on every execution. */
final class PluginSchemas {
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private final Map<String, Schema> inputs = new LinkedHashMap<>();
    private final Map<String, Schema> outputs = new LinkedHashMap<>();
    private final Schema configuration;

    PluginSchemas(PluginManifest manifest) {
        configuration = compile(manifest.configurationSchema());
        manifest.actions().forEach(action -> {
            inputs.put(action.id(), compile(action.inputSchema()));
            outputs.put(action.id(), compile(action.outputSchema()));
        });
    }

    void input(String action, Object value) {
        validate(inputs.get(action), value, "input");
    }

    void output(String action, Object value) {
        validate(outputs.get(action), value, "output");
    }

    void configuration(Map<String, Object> values, Map<String, Object> credentials) {
        var merged = new LinkedHashMap<>(values);
        merged.putAll(credentials);
        validate(configuration, merged, "configuration");
    }

    private Schema compile(String json) {
        if (json == null || json.isBlank()) return null;
        JsonNode schema = JsonUtils.readTree(json);
        checkReferences(schema);
        return registry.getSchema(json, InputFormat.JSON);
    }

    private static void checkReferences(JsonNode value) {
        if (value.isObject()) {
            value.fields().forEachRemaining(field -> {
                if ((field.getKey().equals("$ref") || field.getKey().equals("$dynamicRef"))
                        && (!field.getValue().isTextual()
                                || !field.getValue().asText().startsWith("#")))
                    throw new IllegalArgumentException(
                            "Plugin schemas only support references inside the same document");
                if (field.getKey().equals("$schema")
                        && !field.getValue().asText().equals("https://json-schema.org/draft/2020-12/schema"))
                    throw new IllegalArgumentException("Plugin schemas use JSON Schema draft 2020-12");
                checkReferences(field.getValue());
            });
        } else if (value.isArray()) value.forEach(PluginSchemas::checkReferences);
    }

    private static void validate(Schema schema, Object value, String boundary) {
        if (schema != null
                && !schema.validate(JsonUtils.mapper().valueToTree(value).toString(), InputFormat.JSON)
                        .isEmpty())
            // Validator messages can include credential values; never propagate those into logs/API errors.
            throw new ConnectorException(
                    "plugin_" + boundary + "_invalid", "Plugin " + boundary + " does not match its JSON Schema");
    }
}
