package io.aigoodle.runtime.agentscope;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.aigoodle.common.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import reactor.core.publisher.Flux;

/** Uses platform model instances while leaving tool execution to the SDK toolkit. */
public final class SpringAiAgentScopeModel implements Model {
    private final ChatModel model;
    private final String name;
    private final ChatOptions options;
    private final Runnable beforeExecution;

    public SpringAiAgentScopeModel(ChatModel model, String name, ChatOptions options) {
        this(model, name, options, () -> {});
    }

    SpringAiAgentScopeModel(ChatModel model, String name, ChatOptions options, Runnable beforeExecution) {
        this.model = Objects.requireNonNull(model, "model");
        this.name = Objects.requireNonNull(name, "name");
        this.options = options;
        this.beforeExecution = Objects.requireNonNull(beforeExecution, "beforeExecution");
    }

    @Override
    public String getModelName() {
        return name;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions generation) {
        return Flux.defer(() -> {
            beforeExecution.run();
            var aggregate = new AtomicReference<org.springframework.ai.chat.model.ChatResponse>();
            Prompt prompt = new Prompt(
                    messages.stream().map(SpringAiAgentScopeModel::message).toList(),
                    requestOptions(tools, generation));
            var deltas = new MessageAggregator()
                    .aggregate(model.stream(prompt), aggregate::set)
                    .handle((response, sink) -> {
                        if (response.getResult() != null) {
                            String text = response.getResult().getOutput().getText();
                            if (text != null && !text.isEmpty())
                                sink.next(ChatResponse.builder()
                                        .content(List.of(
                                                TextBlock.builder().text(text).build()))
                                        .build());
                        }
                    });
            return deltas.cast(ChatResponse.class).concatWith(Flux.defer(() -> {
                var response = aggregate.get();
                if (response == null || response.getResult() == null) {
                    return Flux.error(new IllegalStateException("Model stream ended without a response"));
                }
                List<ContentBlock> calls =
                        SpringAiToolCallAssembler.mergeCalls(
                                        response.getResult().getOutput().getToolCalls())
                                .stream()
                                .map(call -> (ContentBlock) ToolUseBlock.builder()
                                        .id(call.id())
                                        .name(call.name())
                                        .input(SpringAiToolCallAssembler.strictArguments(call.arguments()))
                                        .content(call.arguments())
                                        .build())
                                .toList();
                var usage = response.getMetadata().getUsage();
                return Flux.just(ChatResponse.builder()
                        .content(calls)
                        .usage(new ChatUsage(usage.getPromptTokens(), usage.getCompletionTokens(), 0))
                        .finishReason(response.getResult().getMetadata().getFinishReason())
                        .build());
            }));
        });
    }

    private ChatOptions requestOptions(List<ToolSchema> tools, GenerateOptions generation) {
        ToolCallingChatOptions.Builder<?> builder;
        if (options instanceof ToolCallingChatOptions toolOptions) {
            builder = toolOptions.mutate();
        } else {
            builder = ToolCallingChatOptions.builder();
            if (options != null)
                builder.model(options.getModel())
                        .temperature(options.getTemperature())
                        .topP(options.getTopP())
                        .topK(options.getTopK())
                        .maxTokens(options.getMaxTokens())
                        .frequencyPenalty(options.getFrequencyPenalty())
                        .presencePenalty(options.getPresencePenalty())
                        .stopSequences(options.getStopSequences());
        }
        if (generation != null) {
            if (generation.getTemperature() != null) builder.temperature(generation.getTemperature());
            if (generation.getTopP() != null) builder.topP(generation.getTopP());
            if (generation.getTopK() != null) builder.topK(generation.getTopK());
            if (generation.getMaxTokens() != null) builder.maxTokens(generation.getMaxTokens());
            if (generation.getFrequencyPenalty() != null) builder.frequencyPenalty(generation.getFrequencyPenalty());
            if (generation.getPresencePenalty() != null) builder.presencePenalty(generation.getPresencePenalty());
        }
        return builder.toolCallbacks(
                        tools == null
                                ? List.of()
                                : tools.stream()
                                        .map(schema -> (ToolCallback) new SchemaCallback(schema))
                                        .toList())
                .build();
    }

    private static Message message(Msg msg) {
        for (var block : msg.getContent()) {
            if (!(block instanceof TextBlock || block instanceof ToolUseBlock || block instanceof ToolResultBlock)) {
                throw new IllegalArgumentException(
                        "Unsupported AgentScope content: " + block.getClass().getSimpleName());
            }
        }
        String text = msg.getTextContent();
        return switch (msg.getRole()) {
            case SYSTEM -> new SystemMessage(text);
            case USER -> new UserMessage(text);
            case ASSISTANT -> AssistantMessage.builder()
                    .content(text)
                    .toolCalls(msg.getContentBlocks(ToolUseBlock.class).stream()
                            .map(call -> new AssistantMessage.ToolCall(
                                    call.getId(), "function", call.getName(), JsonUtils.toJson(call.getInput())))
                            .toList())
                    .build();
            case TOOL -> {
                var results = new ArrayList<ToolResponseMessage.ToolResponse>();
                for (var result : msg.getContentBlocks(ToolResultBlock.class)) {
                    StringBuilder output = new StringBuilder();
                    for (var block : result.getOutput()) {
                        if (!(block instanceof TextBlock value))
                            throw new IllegalArgumentException("Unsupported tool result content");
                        output.append(value.getText());
                    }
                    results.add(
                            new ToolResponseMessage.ToolResponse(result.getId(), result.getName(), output.toString()));
                }
                yield ToolResponseMessage.builder().responses(results).build();
            }
        };
    }

    private record SchemaCallback(ToolSchema schema) implements ToolCallback {
        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return new DefaultToolDefinition(
                    schema.getName(), schema.getDescription(), JsonUtils.toJson(schema.getParameters()));
        }

        @Override
        public String call(String input) {
            throw new IllegalStateException("Tool execution belongs to the AgentScope toolkit");
        }
    }
}
