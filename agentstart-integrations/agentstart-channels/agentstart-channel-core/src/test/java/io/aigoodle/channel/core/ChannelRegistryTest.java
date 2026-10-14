package io.aigoodle.channel.core;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.channel.api.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelRegistryTest {
    @Test
    void registersAndResolvesCaseInsensitively() {
        ChannelRegistry registry = new ChannelRegistry(List.of(channel("qqbot", "qqbot")));
        assertEquals("qqbot", registry.require("QQBOT").id());
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelRegistry(List.of(channel("qqbot", "qqbot"), channel("QQBOT", "QQBOT"))));
    }

    @Test
    void rejectsDescriptorMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelRegistry(List.of(channel("qqbot", "other"))));
    }

    private Channel<Map> channel(String id, String descriptorId) {
        return new Channel<>() {
            public String id() {
                return id;
            }

            public Class<Map> configType() {
                return Map.class;
            }

            public ChannelDescriptor descriptor() {
                return new ChannelDescriptor(descriptorId, id, "", "1", ChannelCapabilities.text(), Map.of());
            }

            public ConnectionTestResult test(Map value) {
                return ConnectionTestResult.ok();
            }

            public SendResult send(Map value, OutboundMessage message) {
                return SendResult.accepted("1");
            }
        };
    }
}
