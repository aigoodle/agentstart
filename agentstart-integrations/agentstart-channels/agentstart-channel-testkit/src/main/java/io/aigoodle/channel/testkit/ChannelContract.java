package io.aigoodle.channel.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.channel.api.Channel;
import org.junit.jupiter.api.Test;

public interface ChannelContract<C> {
    Channel<C> channel();

    C validConfiguration();

    @Test
    default void hasStableMatchingIdentity() {
        assertFalse(channel().id().isBlank());
        assertEquals(channel().id(), channel().descriptor().id());
    }

    @Test
    default void declaresConfigurationAndCapabilities() {
        assertNotNull(channel().configType());
        assertNotNull(channel().descriptor().capabilities());
        assertNotNull(channel().descriptor().accountModel());
    }

    @Test
    default void validConfigurationCanBeTested() {
        assertNotNull(channel().test(validConfiguration()));
    }
}
