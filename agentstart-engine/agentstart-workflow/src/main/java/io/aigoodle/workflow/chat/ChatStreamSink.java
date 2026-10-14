package io.aigoodle.workflow.chat;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe token channel shared by streaming workflow nodes. Every accepted
 * chunk is accumulated for the final answer and optionally forwarded to an SSE
 * or other live consumer.
 */
public final class ChatStreamSink {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamSink.class);

    private final Consumer<String> liveConsumer;
    private final StringBuilder accumulatedText = new StringBuilder();
    private final Object monitor = new Object();

    private volatile boolean closed;

    public ChatStreamSink(Consumer<String> liveConsumer) {
        this.liveConsumer = liveConsumer;
    }

    /** Returns whether a live consumer is attached and streaming should be enabled. */
    public boolean isStreaming() {
        return liveConsumer != null;
    }

    /** Accumulates and forwards a non-empty chunk unless the sink has been closed. */
    public void push(String chunk) {
        if (chunk == null || chunk.isEmpty() || closed) {
            return;
        }
        synchronized (monitor) {
            if (closed) {
                return;
            }
            accumulatedText.append(chunk);
            notifyLiveConsumer(chunk);
        }
    }

    /** Closes the sink; subsequent chunks are ignored. */
    public void close() {
        synchronized (monitor) {
            closed = true;
        }
    }

    /** Returns a stable snapshot of all chunks accepted so far. */
    public String accumulated() {
        synchronized (monitor) {
            return accumulatedText.toString();
        }
    }

    private void notifyLiveConsumer(String chunk) {
        if (liveConsumer == null) {
            return;
        }
        try {
            liveConsumer.accept(chunk);
        } catch (Exception consumerFailure) {
            // A disconnected live client must not fail the workflow itself.
            log.debug("Streaming consumer rejected a workflow chunk: {}", consumerFailure.getMessage());
        }
    }
}
