package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.aigoodle.channel.persistence.ChannelConnectionMapper;
import io.aigoodle.channel.persistence.ChannelEventMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DatabaseChannelOperationalSnapshotProviderTest {
    @Test
    void aggregatesWithoutTenantDimensionsAndCachesOneScrapeSnapshot() {
        ChannelEventMapper events = mock(ChannelEventMapper.class);
        ChannelConnectionMapper connections = mock(ChannelConnectionMapper.class);
        when(events.selectCount(any())).thenReturn(3L, 2L, 4L, 1L);
        when(connections.selectCount(any())).thenReturn(8L, 5L, 2L, 6L);
        var provider = new DatabaseChannelOperationalSnapshotProvider(events, connections, 8, Duration.ofMinutes(1));

        var first = provider.snapshot();
        var second = provider.snapshot();

        assertThat(first).isEqualTo(new ChannelOperationalSnapshotProvider.Snapshot(3, 2, 4, 1, 8, 5, 2, 6));
        assertThat(second).isSameAs(first);
        verify(events, times(4)).selectCount(any());
        verify(connections, times(4)).selectCount(any());
    }
}
