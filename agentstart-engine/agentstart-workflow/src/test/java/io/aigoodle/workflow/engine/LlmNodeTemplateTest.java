package io.aigoodle.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.PromptTemplateEntity;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.builtin.EndNodeExecutor;
import io.aigoodle.workflow.node.builtin.LlmNodeExecutor;
import io.aigoodle.workflow.node.builtin.StartNodeExecutor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

/**
 * Exercises the {@code systemPromptTemplateId} shortcut on the LLM node — the config
 * must (a) load the template via {@link PromptTemplateService}, (b) render its
 * placeholders against the variable pool, and (c) hand the rendered text to the chat
 * client as the system prompt.
 */
class LlmNodeTemplateTest {

    @Test
    void systemPromptTemplateIsRenderedAgainstPool() {
        // A tiny mock chat client that records what it's asked.
        var capture = new java.util.concurrent.atomic.AtomicReference<String>();
        ChatClient client = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        when(client.prompt()).thenReturn(spec);
        // LlmNodeExecutor builds the full message list and hands it in via
        // messages(...) — Spring AI 1.1.2's spec.system(String) / spec.user(String)
        // shortcut installs Call-only prompt advisors that would break the
        // streaming path, so this test locks in the messages(...) contract.
        when(spec.messages(anyList())).thenAnswer(inv -> {
            List<Message> msgs = inv.getArgument(0);
            msgs.stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .findFirst()
                    .ifPresent(sm -> capture.set(sm.getText()));
            return spec;
        });
        var call = mock(ChatClient.CallResponseSpec.class);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("ok");

        // Wire a ModelService whose getChatClient returns our mock.
        ModelService modelService = mock(ModelService.class);
        when(modelService.getChatClient(anyString(), eq("m1"))).thenReturn(client);

        // A tiny PromptTemplateService that returns our fixture template by id.
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        PromptTemplateEntity tpl = new PromptTemplateEntity();
        tpl.setContent("Answer this: {{#question#}} (locale={{#locale#}})");
        when(prompts.get("tpl-1")).thenReturn(tpl);
        when(prompts.render(anyString(), org.mockito.ArgumentMatchers.anyMap())).thenCallRealMethod();

        List<NodeExecutor> execs = List.of(
                new StartNodeExecutor(),
                new EndNodeExecutor(),
                new LlmNodeExecutor(modelService, new RealRenderPrompts(prompts, tpl)));
        WorkflowEngine engine = new WorkflowEngine(new NodeExecutorRegistry(execs));

        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("llm", NodeType.LLM)
                .with("modelId", "m1")
                .with("systemPromptTemplateId", "tpl-1")
                .with("userPrompt", "hi"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("out", "{{#llm.text#}}")));
        g.addEdge(EdgeDef.of("start", "llm"));
        g.addEdge(EdgeDef.of("llm", "end"));

        WorkflowRunResult r = engine.run(g, Map.of("question", "why?", "locale", "en"), null);
        assertTrue(r.isSuccess(), r.getError());
        String captured = capture.get();
        assertNotNull(captured, "system prompt should be captured");
        assertEquals("Answer this: why? (locale=en)", captured);
    }

    /**
     * A real PromptTemplateService instance would need a DB. Here we build a tiny
     * subclass that uses the provided in-memory fixture instead — cleaner than
     * stubbing four Mockito methods to compose the render pipeline.
     */
    private static final class RealRenderPrompts extends PromptTemplateService {
        private final PromptTemplateEntity fixture;

        RealRenderPrompts(PromptTemplateService delegate, PromptTemplateEntity fixture) {
            super(null); // mapper not used by our overrides
            this.fixture = fixture;
        }

        @Override
        public PromptTemplateEntity get(String id) {
            return fixture;
        }

        @Override
        public PromptTemplateEntity get(String tenantId, String id) {
            return fixture;
        }
    }

    @SuppressWarnings("unused")
    private static ChatClientResponse ignored() {
        return null;
    }
}
