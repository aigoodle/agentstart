package io.aigoodle.workflow.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import reactor.core.publisher.Flux;

/**
 * Replayable handle for an LLM token stream.
 * <p>
 * The source is cached so Spring AI's one-shot advisor chain is subscribed only
 * once. Callers may consume live tokens through {@link #stream()}, wait for the
 * complete message through {@link #getFutureMessage()}, or inspect the current
 * non-blocking {@link #snapshot()}.
 */
public final class ChatFluxHandle {

    private static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(5);

    private final Flux<String> replayableStream;
    private final StringBuilder accumulatedText = new StringBuilder();
    private final CountDownLatch completionSignal = new CountDownLatch(1);

    private volatile boolean complete;
    private volatile Throwable streamFailure;

    public ChatFluxHandle(Flux<String> sourceStream) {
        Flux<String> requiredSource = Objects.requireNonNull(sourceStream, "sourceStream must not be null");
        this.replayableStream = requiredSource
                .doOnNext(this::accumulate)
                .doOnError(this::recordFailure)
                .doOnComplete(this::recordCompletion)
                .cache();
    }

    /** Returns the cached stream, triggering the upstream source on first subscription. */
    @JsonIgnore
    public Flux<String> stream() {
        return replayableStream;
    }

    /** Waits for stream completion and returns the full accumulated message. */
    @JsonIgnore
    public String getFutureMessage() {
        if (!complete) {
            triggerSubscription();
            awaitCompletion();
        }
        propagateFailure();
        return snapshot();
    }

    /** Allows template rendering to treat the handle as its eventual text value. */
    @Override
    public String toString() {
        return getFutureMessage();
    }

    /** Returns the text accumulated so far without subscribing or blocking. */
    @JsonValue
    public String snapshot() {
        synchronized (accumulatedText) {
            return accumulatedText.toString();
        }
    }

    /** Returns whether the stream completed normally or exceptionally. */
    @JsonIgnore
    public boolean isComplete() {
        return complete;
    }

    private void accumulate(String token) {
        if (token == null) {
            return;
        }
        synchronized (accumulatedText) {
            accumulatedText.append(token);
        }
    }

    private void recordFailure(Throwable failure) {
        streamFailure = failure;
        signalCompletion();
    }

    private void recordCompletion() {
        signalCompletion();
    }

    private void signalCompletion() {
        complete = true;
        completionSignal.countDown();
    }

    private void triggerSubscription() {
        replayableStream.subscribe(tokenAlreadyAccumulated -> {}, failureAlreadyRecorded -> {});
    }

    private void awaitCompletion() {
        try {
            boolean completedInTime = completionSignal.await(COMPLETION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completedInTime) {
                throw new IllegalStateException("Timed out waiting for the LLM stream after " + COMPLETION_TIMEOUT);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the LLM stream", interrupted);
        }
    }

    private void propagateFailure() {
        if (streamFailure == null) {
            return;
        }
        if (streamFailure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        throw new IllegalStateException("LLM stream failed", streamFailure);
    }
}
