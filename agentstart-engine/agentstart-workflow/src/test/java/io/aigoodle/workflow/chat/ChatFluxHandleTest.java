package io.aigoodle.workflow.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.util.JsonUtils;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ChatFluxHandleTest {

    @Test
    void resolvesTheCompleteMessageAndReplaysOneUpstreamSubscription() {
        AtomicInteger subscriptions = new AtomicInteger();
        ChatFluxHandle handle = new ChatFluxHandle(Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just("Hello", " ", "world");
        }));

        assertThat(handle.snapshot()).isEmpty();
        assertThat(handle.getFutureMessage()).isEqualTo("Hello world");
        assertThat(handle.stream().collectList().block()).containsExactly("Hello", " ", "world");
        assertThat(handle.toString()).isEqualTo("Hello world");
        assertThat(handle.isComplete()).isTrue();
        assertThat(subscriptions).hasValue(1);
    }

    @Test
    void propagatesTheOriginalRuntimeFailureAfterTermination() {
        IllegalStateException upstreamFailure = new IllegalStateException("provider unavailable");
        ChatFluxHandle handle = new ChatFluxHandle(Flux.error(upstreamFailure));

        assertThatThrownBy(handle::getFutureMessage).isSameAs(upstreamFailure);
        assertThatThrownBy(handle::getFutureMessage).isSameAs(upstreamFailure);
        assertThat(handle.isComplete()).isTrue();
    }

    @Test
    void rejectsAMissingSourceStreamAtTheBoundary() {
        assertThatThrownBy(() -> new ChatFluxHandle(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceStream must not be null");
    }

    @Test
    void wrapsCheckedUpstreamFailureWithReadableContext() {
        IOException upstreamFailure = new IOException("socket closed");
        ChatFluxHandle handle = new ChatFluxHandle(Flux.error(upstreamFailure));

        assertThatThrownBy(handle::getFutureMessage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LLM stream failed")
                .hasCause(upstreamFailure);
    }

    @Test
    void jsonSerializationUsesCurrentSnapshotWithoutStartingStream() {
        AtomicInteger subscriptions = new AtomicInteger();
        ChatFluxHandle handle = new ChatFluxHandle(Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just("later");
        }));

        assertThat(JsonUtils.toJson(handle)).isEqualTo("\"\"");
        assertThat(subscriptions).hasValue(0);
        assertThat(handle.isComplete()).isFalse();
    }
}
