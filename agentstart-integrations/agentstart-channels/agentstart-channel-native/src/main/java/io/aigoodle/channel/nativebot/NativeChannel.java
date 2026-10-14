package io.aigoodle.channel.nativebot;

import io.aigoodle.channel.api.Channel;
import io.aigoodle.channel.api.InboundMessage;
import java.util.Map;

/** Optional webhook boundary implemented by native bot channels. */
public interface NativeChannel<C> extends Channel<C> {
    Map<String, Object> credentialSchema();

    default Map<String, Object> configurationSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    default InboundMessage parse(C configuration, Map<String, String> headers, Map<String, Object> payload) {
        throw new UnsupportedOperationException(id() + " does not receive HTTP callbacks");
    }

    default Object challenge(C configuration, Map<String, String> headers, Map<String, Object> payload) {
        return null;
    }
}
