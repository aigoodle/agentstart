package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.chat.ChatFluxHandle;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.variable.VariablePool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class AnswerTemplateRendererTest {

    private final AnswerTemplateRenderer renderer = new AnswerTemplateRenderer();
    private final VariablePool variables = new VariablePool();

    @Test
    void rendersLiteralAndOrdinaryVariableValuesInOrder() {
        variables.setSystem("name", "Alice");
        variables.setSystem("count", 3);

        String answer = renderer.render("Hello {{#sys.name#}}, count={{#sys.count#}}.", variables, null);

        assertThat(answer).isEqualTo("Hello Alice, count=3.");
    }

    @Test
    void streamsHandleTokensBetweenLiteralChunks() {
        variables.setSystem("answer", new ChatFluxHandle(Flux.just("A", "B")));
        List<String> streamedChunks = new ArrayList<>();
        ChatStreamSink streamSink = new ChatStreamSink(streamedChunks::add);

        String answer = renderer.render("Before {{#sys.answer#}} after", variables, streamSink);

        assertThat(answer).isEqualTo("Before AB after");
        assertThat(streamedChunks).containsExactly("Before ", "A", "B", " after");
        assertThat(streamSink.accumulated()).isEqualTo(answer);
    }

    @Test
    void blockingRenderConsumesStreamHandleToCompletion() {
        variables.setSystem("answer", new ChatFluxHandle(Flux.just("one", "two")));

        String answer = renderer.render("{{#sys.answer#}}", variables, null);

        assertThat(answer).isEqualTo("onetwo");
    }

    @Test
    void missingVariableProducesNoSyntheticText() {
        String answer = renderer.render("A{{#sys.missing#}}B", variables, null);

        assertThat(answer).isEqualTo("AB");
    }
}
