package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelCatalogServiceTest {
    @Test
    void cachesDiscoveryAndSupportsForcedRefreshAndInvalidation() {
        ChannelRuntimeRegistry registry = mock(ChannelRuntimeRegistry.class);
        when(registry.discover(null)).thenReturn(List.of());
        ChannelCatalogService catalog = new ChannelCatalogService(registry, Duration.ofMinutes(10));

        assertThat(catalog.get("tenant-a", null, false).cached()).isFalse();
        assertThat(catalog.get("tenant-a", null, false).cached()).isTrue();
        verify(registry, times(1)).discover(null);

        catalog.get("tenant-a", null, true);
        verify(registry, times(2)).discover(null);
        catalog.invalidate();
        catalog.get("tenant-a", null, false);
        verify(registry, times(3)).discover(null);
    }

    @Test
    void isolatesCacheByTenantAndRuntimeNode() {
        ChannelRuntimeRegistry registry = mock(ChannelRuntimeRegistry.class);
        when(registry.discover(any())).thenReturn(List.of());
        ChannelCatalogService catalog = new ChannelCatalogService(registry, Duration.ofMinutes(10));

        catalog.get("tenant-a", "openclaw-1", false);
        catalog.get("tenant-a", "openclaw-1", false);
        catalog.get("tenant-b", "openclaw-1", false);
        catalog.get("tenant-a", "openclaw-2", false);

        verify(registry, times(2)).discover("openclaw-1");
        verify(registry).discover("openclaw-2");

        catalog.invalidate("tenant-a", "openclaw-1");
        catalog.get("tenant-a", "openclaw-1", false);
        verify(registry, times(3)).discover("openclaw-1");
    }
}
