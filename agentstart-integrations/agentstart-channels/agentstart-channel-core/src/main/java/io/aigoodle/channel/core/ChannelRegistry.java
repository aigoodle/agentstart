package io.aigoodle.channel.core;

import io.aigoodle.channel.api.Channel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChannelRegistry {
    private final Map<String, Channel<?>> channels;

    public ChannelRegistry(List<Channel<?>> discovered) {
        Map<String, Channel<?>> values = new LinkedHashMap<>();
        for (Channel<?> channel : discovered == null ? List.<Channel<?>>of() : discovered) {
            String id = normalize(channel.id());
            if (!id.equals(normalize(channel.descriptor().id())))
                throw new IllegalArgumentException("Channel descriptor id does not match: " + id);
            if (channel.configType() == null)
                throw new IllegalArgumentException("Channel configType is required: " + id);
            if (values.putIfAbsent(id, channel) != null) throw new IllegalArgumentException("Duplicate channel: " + id);
        }
        channels = Map.copyOf(values);
    }

    public List<Channel<?>> all() {
        return List.copyOf(channels.values());
    }

    public Channel<?> require(String id) {
        Channel<?> channel = channels.get(normalize(id));
        if (channel == null) throw new IllegalArgumentException("Unknown channel: " + id);
        return channel;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Channel id is required");
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
