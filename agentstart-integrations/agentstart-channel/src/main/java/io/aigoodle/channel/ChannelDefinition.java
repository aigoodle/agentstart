package io.aigoodle.channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-neutral description of a continuously running inbound/outbound channel. */
public record ChannelDefinition(
        String provider,
        String channelId,
        String name,
        String description,
        String version,
        boolean installed,
        boolean enabled,
        String runtimeStatus,
        String credentialSchema,
        String configurationSchema,
        Map<String, Object> uiSchema,
        Map<String, Object> capabilities,
        Map<String, Object> metadata) {

    public ChannelDefinition {
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channelId is required");
        name = name == null || name.isBlank() ? channelId : name;
        description = description == null || description.isBlank() ? name : description;
        runtimeStatus = runtimeStatus == null || runtimeStatus.isBlank() ? "UNKNOWN" : runtimeStatus;
        uiSchema = immutableWithoutNulls(uiSchema);
        capabilities = immutableWithoutNulls(ChannelInteractionCapabilities.normalize(channelId, capabilities));
        metadata = immutableWithoutNulls(metadata);
    }

    private static Map<String, Object> immutableWithoutNulls(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) values.put(key, value);
        });
        return values.isEmpty() ? Map.of() : Collections.unmodifiableMap(values);
    }
}
