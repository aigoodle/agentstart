package io.aigoodle.tool.custom;

import io.aigoodle.common.crypto.AesGcmTextEncryptor;
import io.aigoodle.common.crypto.TextEncryptor;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Owns durable, page-created HTTP tools and contributes enabled entries to ToolRegistry. */
public final class CustomToolManager implements ToolProvider {
    private final List<CustomHttpToolSpec> specs = new ArrayList<>();
    private final Path configFile;
    private final TextEncryptor encryptor;

    public CustomToolManager(CustomToolProperties properties) {
        this.configFile =
                properties.getConfigFile() == null || properties.getConfigFile().isBlank()
                        ? null
                        : Path.of(properties.getConfigFile()).toAbsolutePath().normalize();
        this.encryptor = new AesGcmTextEncryptor(properties.getEncryptionSecret());
        load();
    }

    public synchronized List<CustomHttpToolSpec> specs() {
        return specs.stream().map(CustomToolManager::publicCopy).toList();
    }

    public synchronized void upsert(CustomHttpToolSpec spec) {
        validate(spec);
        specs.removeIf(existing -> existing.getName().equals(spec.getName()));
        specs.add(copy(spec));
        persist();
    }

    public synchronized boolean remove(String name) {
        boolean removed = specs.removeIf(spec -> spec.getName().equals(name));
        if (removed) persist();
        return removed;
    }

    @Override
    public synchronized List<ToolDefinition> getTools() {
        return specs.stream()
                .filter(CustomHttpToolSpec::isEnabled)
                .map(CustomToolManager::copy)
                .map(CustomHttpToolDefinition::new)
                .map(ToolDefinition.class::cast)
                .toList();
    }

    private record StoredTool(
            String name,
            String description,
            String method,
            String url,
            String inputSchema,
            boolean enabled,
            String encryptedHeaders) {}

    private void load() {
        if (configFile == null || !Files.isRegularFile(configFile)) return;
        try {
            for (StoredTool stored : JsonUtils.parseList(Files.readString(configFile), StoredTool.class)) {
                CustomHttpToolSpec spec = new CustomHttpToolSpec();
                spec.setName(stored.name());
                spec.setDescription(stored.description());
                spec.setMethod(stored.method());
                spec.setUrl(stored.url());
                spec.setInputSchema(stored.inputSchema());
                spec.setEnabled(stored.enabled());
                String headers = encryptor.decrypt(stored.encryptedHeaders());
                @SuppressWarnings("unchecked")
                Map<String, String> values = headers == null
                        ? Map.of()
                        : JsonUtils.mapper().convertValue(JsonUtils.parseMap(headers), Map.class);
                spec.setHeaders(values);
                specs.add(spec);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to load custom tools from " + configFile, failure);
        }
    }

    private void persist() {
        if (configFile == null) return;
        try {
            Path parent = configFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            List<StoredTool> stored = specs.stream()
                    .map(spec -> new StoredTool(
                            spec.getName(),
                            spec.getDescription(),
                            spec.getMethod(),
                            spec.getUrl(),
                            spec.getInputSchema(),
                            spec.isEnabled(),
                            encryptor.encrypt(JsonUtils.toJson(spec.getHeaders()))))
                    .toList();
            Path temporary = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(temporary, JsonUtils.toJson(stored), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to persist custom tools to " + configFile, failure);
        }
    }

    private static void validate(CustomHttpToolSpec spec) {
        if (spec == null || spec.getName() == null || !spec.getName().matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))
            throw new IllegalArgumentException(
                    "Tool name must start with a letter and contain only letters, digits, _ or -");
        if (spec.getDescription() == null || spec.getDescription().isBlank())
            throw new IllegalArgumentException("Tool description is required");
        if (spec.getUrl() == null
                || !(spec.getUrl().startsWith("http://") || spec.getUrl().startsWith("https://")))
            throw new IllegalArgumentException("Tool URL must use http or https");
        String method = spec.getMethod() == null ? "POST" : spec.getMethod().toUpperCase();
        if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method))
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        spec.setMethod(method);
        JsonUtils.parseMap(spec.getInputSchema());
    }

    private static CustomHttpToolSpec copy(CustomHttpToolSpec source) {
        CustomHttpToolSpec target = new CustomHttpToolSpec();
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setMethod(source.getMethod());
        target.setUrl(source.getUrl());
        target.setInputSchema(source.getInputSchema());
        target.setEnabled(source.isEnabled());
        target.setHeaders(new java.util.LinkedHashMap<>(source.getHeaders()));
        return target;
    }

    private static CustomHttpToolSpec publicCopy(CustomHttpToolSpec source) {
        CustomHttpToolSpec target = copy(source);
        target.setHeaders(source.getHeaders().keySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        key -> key, ignored -> "********", (a, b) -> b, java.util.LinkedHashMap::new)));
        return target;
    }
}
