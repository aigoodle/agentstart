package io.aigoodle.workflow.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatStreamSinkTest {

    @Test
    void accumulatesAndForwardsAcceptedChunks() {
        List<String> forwarded = new ArrayList<>();
        ChatStreamSink sink = new ChatStreamSink(forwarded::add);

        sink.push("Hello");
        sink.push(" ");
        sink.push("world");

        assertThat(sink.isStreaming()).isTrue();
        assertThat(sink.accumulated()).isEqualTo("Hello world");
        assertThat(forwarded).containsExactly("Hello", " ", "world");
    }

    @Test
    void closeRejectsSubsequentChunks() {
        ChatStreamSink sink = new ChatStreamSink(null);
        sink.push("before");

        sink.close();
        sink.push("after");

        assertThat(sink.isStreaming()).isFalse();
        assertThat(sink.accumulated()).isEqualTo("before");
    }

    @Test
    void consumerFailureDoesNotDiscardAccumulatedAnswerOrFailWorkflow() {
        ChatStreamSink sink = new ChatStreamSink(chunk -> {
            throw new IllegalStateException("client disconnected");
        });

        assertThatCode(() -> sink.push("answer")).doesNotThrowAnyException();
        assertThat(sink.accumulated()).isEqualTo("answer");
    }
}
