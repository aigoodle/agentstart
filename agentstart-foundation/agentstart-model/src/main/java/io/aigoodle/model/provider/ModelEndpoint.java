package io.aigoodle.model.provider;

import io.aigoodle.model.enums.ModelType;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * A fully-resolved, decrypted model configuration handed to a {@link ModelProvider}
 * so it can build the concrete Spring AI model object.
 * <p>
 * This is intentionally a flat value object: persistence, encryption and tenant
 * concerns are resolved <em>before</em> a provider ever sees it.
 */
@Data
@Builder
@lombok.extern.jackson.Jacksonized
public class ModelEndpoint {

    /** Trusted host tenant scope; participates in every reusable runtime cache key. */
    @Builder.Default
    private String tenantId = "default";

    /** Stable identifier of the backing persisted model row (used as cache key). */
    private String id;

    private String providerName;

    private String modelName;

    private ModelType modelType;

    /** OpenAI-compatible base url; null lets the provider use its default. */
    private String baseUrl;

    private String apiKey;

    /** Provider-specific extras (e.g. {@code dimensions}, {@code endpointId}, {@code organization}). */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    public String property(String name) {
        Object value = properties == null ? null : properties.get(name);
        return value == null ? null : String.valueOf(value);
    }

    public String propertyOrDefault(String name, String defaultValue) {
        String value = property(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public Integer intProperty(String name) {
        String value = property(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public Double decimalProperty(String name) {
        String value = property(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Resolves a property override, the endpoint column, then the provider default. */
    public String resolveBaseUrl(String providerDefault) {
        String endpointBaseUrl = baseUrl == null || baseUrl.isBlank() ? providerDefault : baseUrl;
        return propertyOrDefault("baseUrl", endpointBaseUrl);
    }
}
