package io.aigoodle.channel.nativebot;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads channel-owned frontend contracts from every installed channel jar. */
final class ChannelManifestCatalog {
    static final String LOCATION = "classpath*:META-INF/agent-start/channels/*.yml";

    record Manifest(
            int schemaVersion,
            String id,
            String name,
            String description,
            String version,
            Map<String, Object> credentialSchema,
            Map<String, Object> configurationSchema,
            Map<String, Object> uiSchema,
            Map<String, Object> accountModel,
            Map<String, Object> metadata) {}

    private ChannelManifestCatalog() {}

    static Map<String, Manifest> load() {
        try {
            Map<String, Manifest> result = new LinkedHashMap<>();
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            for (Resource resource : resources) {
                Manifest manifest = read(resource);
                Manifest previous = result.putIfAbsent(manifest.id(), manifest);
                if (previous != null)
                    throw new IllegalStateException("Duplicate channel manifest id: " + manifest.id());
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot scan channel manifests", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Manifest read(Resource resource) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        Object loaded;
        try (InputStream input = resource.getInputStream()) {
            loaded = new Yaml(new SafeConstructor(options)).load(input);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read channel manifest " + resource, e);
        }
        if (!(loaded instanceof Map<?, ?> source)) throw invalid(resource, "root must be an object");
        Map<String, Object> root = stringMap(source, resource, "root");
        String id = text(root.get("id"));
        if (id == null) throw invalid(resource, "id is required");
        if (!(root.get("schemaVersion") instanceof Number version) || version.intValue() != 1)
            throw invalid(resource, "schemaVersion must be 1");
        Map<String, Object> credentials = object(root, "credentialSchema", resource);
        Map<String, Object> configuration = object(root, "configurationSchema", resource);
        validateSchema(credentials, resource, "credentialSchema");
        validateSchema(configuration, resource, "configurationSchema");
        Map<String, Object> account = object(root, "accountModel", resource);
        validateAccountModel(account, resource);
        return new Manifest(
                1,
                id,
                text(root.get("name")),
                text(root.get("description")),
                text(root.get("version")),
                credentials,
                configuration,
                object(root, "uiSchema", resource),
                account,
                object(root, "metadata", resource));
    }

    private static void validateSchema(Map<String, Object> schema, Resource resource, String field) {
        if (schema.isEmpty()) return;
        if (!"object".equals(schema.get("type"))) throw invalid(resource, field + ".type must be object");
        Object rawProperties = schema.get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties))
            throw invalid(resource, field + ".properties must be an object");
        Object rawRequired = schema.get("required");
        if (rawRequired instanceof List<?> required) {
            List<String> unknown = new ArrayList<>();
            for (Object name : required)
                if (!properties.containsKey(String.valueOf(name))) unknown.add(String.valueOf(name));
            if (!unknown.isEmpty()) throw invalid(resource, field + ".required has unknown fields " + unknown);
        }
    }

    private static void validateAccountModel(Map<String, Object> model, Resource resource) {
        if (model.isEmpty()) return;
        String scope = text(model.get("scope"));
        if (!List.of("PERSONAL", "TENANT").contains(scope))
            throw invalid(resource, "accountModel.scope must be PERSONAL or TENANT");
        String policy = text(model.get("instancePolicy"));
        if (policy != null && !List.of("SINGLE", "MULTIPLE").contains(policy))
            throw invalid(resource, "accountModel.instancePolicy must be SINGLE or MULTIPLE");
    }

    private static Map<String, Object> object(Map<String, Object> source, String key, Resource resource) {
        Object value = source.get(key);
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw invalid(resource, key + " must be an object");
        return stringMap(map, resource, key);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source, Resource resource, String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name) || name.isBlank())
                throw invalid(resource, path + " contains a non-string or blank key");
            result.put(name, freeze(value, resource, path + "." + name));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object freeze(Object value, Resource resource, String path) {
        if (value instanceof Map<?, ?> map) return stringMap(map, resource, path);
        if (value instanceof List<?> list)
            return list.stream().map(item -> freeze(item, resource, path)).toList();
        return value;
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null
                : String.valueOf(value).trim();
    }

    private static IllegalArgumentException invalid(Resource resource, String message) {
        return new IllegalArgumentException("Invalid channel manifest " + resource + ": " + message);
    }
}
