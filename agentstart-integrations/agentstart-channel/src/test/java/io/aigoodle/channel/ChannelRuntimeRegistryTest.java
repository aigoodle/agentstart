package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelRuntimeRegistryTest {
    @Test
    void addressesMultipleRuntimeNodesForOneProvider() {
        ChannelRuntimeProvider a = runtime("openclaw", "node-a");
        ChannelRuntimeProvider b = runtime("openclaw", "node-b");
        ChannelRuntimeRegistry registry = new ChannelRuntimeRegistry(List.of(a, b));
        assertThat(registry.require("openclaw", "node-b")).isSameAs(b);
        assertThat(registry.nodes()).containsEntry("openclaw", List.of("node-a", "node-b"));
    }

    private static ChannelRuntimeProvider runtime(String type, String node) {
        ChannelRuntimeProvider value = mock(ChannelRuntimeProvider.class);
        when(value.type()).thenReturn(type);
        when(value.nodeId()).thenReturn(node);
        return value;
    }
}
