package io.aigoodle.plugin;

import io.aigoodle.common.util.JsonUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Deployment-owned JSON/YAML descriptors. YAML schemas are ordinary mappings, not escaped JSON. */
public final class PluginManifests {
    private PluginManifests() {}

    public static PluginManifest load(Resource resource) throws IOException {
        String source;
        try (var input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) throw new IllegalArgumentException("Plugin manifest exceeds 1 MiB");
            source = new String(bytes, StandardCharsets.UTF_8);
        }
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setCodePointLimit(1_048_576);
        Object parsed = new Yaml(new SafeConstructor(options)).load(source);
        Map<String, Object> descriptor = mapping(parsed);
        schema(descriptor, "configurationSchema");
        if (descriptor.get("actions") instanceof List<?> actions) {
            var normalized = new ArrayList<Map<String, Object>>();
            for (Object action : actions) {
                var fields = mapping(action);
                schema(fields, "inputSchema");
                schema(fields, "outputSchema");
                normalized.add(fields);
            }
            descriptor.put("actions", normalized);
        }
        if (descriptor.get("skills") instanceof List<?> skills) {
            var loaded = new ArrayList<Object>();
            for (Object item : skills) {
                var fields = mapping(item);
                if (fields.containsKey("path")) {
                    String path = relative(fields.get("path"));
                    List<?> refs = fields.get("references") instanceof List<?> list ? list : List.of();
                    String[] references =
                            refs.stream().map(PluginManifests::relative).toArray(String[]::new);
                    loaded.add(PluginSkills.load(resource.createRelative(path), references));
                } else loaded.add(fields); // preserve existing JSON manifests with inline skills
            }
            descriptor.put("skills", loaded);
        }
        return JsonUtils.convert(descriptor, PluginManifest.class);
    }

    private static Map<String, Object> mapping(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Manifest entries must be objects");
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> {
            if (!(key instanceof String name)) throw new IllegalArgumentException("Manifest keys must be strings");
            result.put(name, item);
        });
        return result;
    }

    private static void schema(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof Map<?, ?> || value instanceof Boolean) fields.put(name, JsonUtils.toJson(value));
        else if (value != null && !(value instanceof String))
            throw new IllegalArgumentException(name + " must be a schema");
    }

    private static String relative(Object value) {
        if (!(value instanceof String path)
                || path.isBlank()
                || path.startsWith("/")
                || path.contains("..")
                || path.contains("\\")
                || path.contains(":"))
            throw new IllegalArgumentException("Manifest resource references must stay inside its directory");
        return path;
    }
}
