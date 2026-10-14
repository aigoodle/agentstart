package io.aigoodle.channel.api;

public interface ChannelSession extends AutoCloseable {
    boolean connected();

    @Override
    void close();

    static ChannelSession noop() {
        return new ChannelSession() {
            public boolean connected() {
                return true;
            }

            public void close() {}
        };
    }
}
