package io.aigoodle.channel.api;

import java.util.Map;

public record ChannelDescriptor(
        String id,
        String name,
        String description,
        String version,
        ChannelCapabilities capabilities,
        ChannelAccountModel accountModel,
        Map<String, Object> metadata) {
    /** Compatibility constructor for third-party channels compiled against the original SPI. */
    public ChannelDescriptor(
            String id,
            String name,
            String description,
            String version,
            ChannelCapabilities capabilities,
            Map<String, Object> metadata) {
        this(id, name, description, version, capabilities, ChannelAccountModel.personal(), metadata);
    }

    public ChannelDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("channel id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("channel name is required");
        version = version == null || version.isBlank() ? "1" : version;
        capabilities = capabilities == null ? ChannelCapabilities.text() : capabilities;
        accountModel = accountModel == null ? ChannelAccountModel.personal() : accountModel;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
