package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

class SpringAiAgentScopeModelTest {
    @Test
    void generationOverridesOnlyExplicitSamplingFieldsAndKeepsApplicationDefaults() {
        var captured = new AtomicReference<Prompt>();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                captured.set(prompt);
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("answer")))));
            }
        };
        var adapter = new SpringAiAgentScopeModel(
                model,
                "offline",
                ToolCallingChatOptions.builder()
                        .model("configured-model")
                        .temperature(0.25)
                        .topP(0.8)
                        .maxTokens(100)
                        .build());
        adapter.stream(
                        List.of(),
                        List.of(),
                        io.agentscope.core.model.GenerateOptions.builder()
                                .temperature(0.6)
                                .maxTokens(250)
                                .build())
                .collectList()
                .block();
        var options = captured.get().getOptions();
        assertEquals(0.6, options.getTemperature());
        assertEquals(250, options.getMaxTokens());
        assertEquals(0.8, options.getTopP());
        assertEquals("configured-model", options.getModel());
        adapter.stream(List.of(), List.of(), null).collectList().block();
        assertEquals(0.25, captured.get().getOptions().getTemperature());
        assertEquals(100, captured.get().getOptions().getMaxTokens());
    }

    @Test
    void interleavedCallsRemainSeparateAndIdenticalCompleteCallsAreDeduplicated() {
        var responses = toolModel(List.of(
                        new AssistantMessage.ToolCall("a", "function", "echo", "{\"value\":"),
                        new AssistantMessage.ToolCall("b", "function", "echo", "{\"value\":2}"),
                        new AssistantMessage.ToolCall("a", "function", null, "1}"),
                        new AssistantMessage.ToolCall("b", "function", "echo", "{\"value\":2}")))
                .stream(List.of(), List.of(), null)
                .collectList()
                .block();
        var calls = responses.getLast().getContent();
        assertEquals(2, calls.size());
        assertEquals(java.util.Map.of("value", 1), ((ToolUseBlock) calls.get(0)).getInput());
        assertEquals(java.util.Map.of("value", 2), ((ToolUseBlock) calls.get(1)).getInput());
    }

    @Test
    void malformedAmbiguousAndConflictingCallsFailBeforeSdkCanExecuteTools() {
        for (var calls : List.of(
                List.of(new AssistantMessage.ToolCall("a", "function", "echo", "{}{}")),
                List.of(
                        new AssistantMessage.ToolCall("a", "function", "echo", "{}"),
                        new AssistantMessage.ToolCall("a", "function", "echo", "{\"different\":true}")),
                List.of(
                        new AssistantMessage.ToolCall("a", "function", "echo", "{}"),
                        new AssistantMessage.ToolCall("a", "function", "other", "{}")),
                List.of(
                        new AssistantMessage.ToolCall("a", "function", "echo", "{}"),
                        new AssistantMessage.ToolCall("b", "function", "echo", "{}"),
                        new AssistantMessage.ToolCall(null, "function", null, "1}")))) {
            assertThrows(IllegalArgumentException.class, () -> toolModel(calls).stream(List.of(), List.of(), null)
                    .collectList()
                    .block());
        }
    }

    private static SpringAiAgentScopeModel toolModel(List<AssistantMessage.ToolCall> calls) {
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.fromIterable(calls)
                        .map(call -> new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(call))
                                .build()))));
            }
        };
        return new SpringAiAgentScopeModel(model, "offline", null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void preservesTextDeltasToolsAndToolResultWithoutExecutingCallbacks(boolean fragmented) {
        var captured = new AtomicReference<Prompt>();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                captured.set(prompt);
                return Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("hello ")))),
                        new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                                .content("world")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-1", "function", "echo", fragmented ? "{\"value\":" : "{\"value\":42}")))
                                .build()))),
                        new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                                .content("")
                                .toolCalls(
                                        fragmented
                                                ? List.of(new AssistantMessage.ToolCall(
                                                        "call-1", "function", null, "42}"))
                                                : List.of())
                                .build()))));
            }
        };
        var adapter = new SpringAiAgentScopeModel(
                model,
                "offline",
                ToolCallingChatOptions.builder().temperature(0.25).build());
        var messages = List.of(
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .content(List.of(new ToolUseBlock("prior", "echo", Map.of("value", 1))))
                        .build(),
                Msg.builder()
                        .role(MsgRole.TOOL)
                        .content(List.of(ToolResultBlock.of(
                                "prior", "echo", TextBlock.builder().text("1").build())))
                        .build());
        var chunks = adapter.stream(
                        messages,
                        List.of(ToolSchema.builder()
                                .name("echo")
                                .description("Echo")
                                .parameters(Map.of("type", "object"))
                                .build()),
                        null)
                .collectList()
                .block();
        assertEquals("hello ", ((TextBlock) chunks.get(0).getContent().getFirst()).getText());
        assertEquals("world", ((TextBlock) chunks.get(1).getContent().getFirst()).getText());
        var call = (ToolUseBlock) chunks.getLast().getContent().getFirst();
        assertEquals("call-1", call.getId());
        assertEquals(Map.of("value", 42), call.getInput());
        var prompt = captured.get();
        assertEquals(
                "prior",
                ((AssistantMessage) prompt.getInstructions().getFirst())
                        .getToolCalls()
                        .getFirst()
                        .id());
        assertEquals(
                "1",
                ((ToolResponseMessage) prompt.getInstructions().getLast())
                        .getResponses()
                        .getFirst()
                        .responseData());
        var options = (ToolCallingChatOptions) prompt.getOptions();
        assertEquals(0.25, options.getTemperature());
        assertEquals(
                "echo",
                options.getToolCallbacks().getFirst().getToolDefinition().name());
        assertThrows(
                IllegalStateException.class,
                () -> options.getToolCallbacks().getFirst().call("{}"));
    }
}
