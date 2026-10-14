package io.aigoodle.channel;

/**
 * Ephemeral live reply attached to an inbound channel event. Implementations are transport-owned;
 * they must not be persisted as part of the durable event or trigger payload.
 */
public interface ChannelReplyStream {

    String METADATA_KEY = "_channelReplyStream";

    /** Opens the platform reply window, normally with a short processing placeholder. */
    void start();

    /** Accepts one incremental text chunk from the workflow. */
    void push(String chunk);

    /** Replaces the live message with the final full answer and closes the stream. */
    void complete(String content);

    /** Closes the stream with a user-visible failure message. */
    void fail(String message);

    static ChannelReplyStream from(ChannelInboundEvent event) {
        if (event == null) return null;
        Object candidate = event.metadata().get(METADATA_KEY);
        return candidate instanceof ChannelReplyStream stream ? stream : null;
    }
}
