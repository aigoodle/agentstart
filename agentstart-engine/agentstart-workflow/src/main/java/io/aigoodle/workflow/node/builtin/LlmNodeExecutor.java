package io.aigoodle.workflow.node.builtin;

import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.workflow.chat.ChatFluxHandle;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Executes an LLM workflow node in structured, streaming or blocking mode.
 * Conversation construction—including saved templates and memory—is delegated
 * to {@link LlmConversationBuilder}, leaving this class focused on model execution.
 */
public class LlmNodeExecutor implements NodeExecutor {

    private final ModelService modelService;
    private final LlmConversationBuilder conversationBuilder;
    private final MemoryManager conversationMemory;

    public LlmNodeExecutor(ModelService modelService) {
        this(modelService, null, null);
    }

    public LlmNodeExecutor(ModelService modelService, PromptTemplateService promptTemplateService) {
        this(modelService, promptTemplateService, null);
    }

    public LlmNodeExecutor(
            ModelService modelService, PromptTemplateService promptTemplateService, MemoryManager conversationMemory) {
        this.modelService = modelService;
        this.conversationMemory = conversationMemory;
        this.conversationBuilder = new LlmConversationBuilder(promptTemplateService, conversationMemory);
    }

    @Override
    public NodeType type() {
        return NodeType.LLM;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        context.throwIfCancelled();
        List<Message> messages = conversationBuilder.build(node, context);
        boolean structuredOutput = requiresStructuredOutput(node);
        if (shouldStream(context.getChatSink(), structuredOutput)) {
            return executeStreaming(node, context, messages);
        }
        return executeBlocking(node, context, messages, structuredOutput);
    }

    private NodeResult executeStreaming(NodeDef node, ExecutionContext context, List<Message> messages) {
        ChatModel chatModel;
        try {
            chatModel = NodeModelResolver.resolveModel(node, context, modelService);
        } catch (IllegalArgumentException exception) {
            return NodeResult.failure("LLM model initialization failed: " + exception.getMessage());
        }

        Prompt prompt = new Prompt(messages, resolveChatOptions(node, chatModel));
        Flux<String> tokens = chatModel.stream(prompt)
                .mapNotNull(LlmNodeExecutor::extractDelta)
                .filter(delta -> !delta.isEmpty())
                .doOnNext(ignored -> context.throwIfCancelled());
        if (context.getCancellationToken() != null) {
            tokens = tokens.takeUntilOther(context.getCancellationToken().cancellationSignal());
        }
        return NodeResult.of("text", new ChatFluxHandle(tokens));
    }

    private NodeResult executeBlocking(
            NodeDef node, ExecutionContext context, List<Message> messages, boolean structuredOutput) {
        ChatClient chatClient;
        try {
            chatClient = NodeModelResolver.resolve(node, context, modelService);
        } catch (IllegalArgumentException exception) {
            return NodeResult.failure("LLM model initialization failed: " + exception.getMessage());
        }

        ChatClient.ChatClientRequestSpec request = chatClient.prompt().messages(messages);
        ChatOptions nodeOptions = NodeModelResolver.perNodeOptions(node);
        if (nodeOptions != null) {
            request = request.options(nodeOptions.mutate());
        }
        ChatResponse response = request.call().chatResponse();
        String content = response == null ? request.call().content() : extractContent(response);
        NodeResult result;
        if (structuredOutput) {
            result = LlmStructuredOutputMapper.map(content);
        } else {
            result = NodeResult.of("text", content);
        }
        attachUsage(node, response, result);
        rememberChannelExchange(context, content);
        context.throwIfCancelled();
        return result;
    }

    private void rememberChannelExchange(ExecutionContext context, String answer) {
        if (conversationMemory == null
                || !Boolean.parseBoolean(String.valueOf(context.getInputs().get("_channel_conversation")))
                || context.getConversationId() == null
                || context.getConversationId().isBlank()) return;
        String query = text(context.getInputs().get("query"));
        String tenant = text(context.getInputs().get("_memory_tenant_id"));
        String owner = text(context.getInputs().get("_memory_owner_id"));
        if (query == null || answer == null || answer.isBlank() || owner == null) return;
        try {
            conversationMemory.rememberExchange(
                    tenant == null ? "default" : tenant, owner, context.getConversationId(), query, answer);
        } catch (RuntimeException ignored) {
            // History persistence is best-effort and must not fail the LLM node.
        }
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static boolean shouldStream(ChatStreamSink streamSink, boolean structuredOutput) {
        return !structuredOutput && streamSink != null && streamSink.isStreaming();
    }

    private static ChatOptions resolveChatOptions(NodeDef node, ChatModel chatModel) {
        ChatOptions nodeOptions = NodeModelResolver.perNodeOptions(node);
        return nodeOptions != null ? nodeOptions : chatModel.getOptions();
    }

    private static String extractDelta(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Generation generation = response.getResult();
        if (generation == null) {
            return null;
        }
        AssistantMessage message = generation.getOutput();
        if (message == null) {
            return null;
        }
        String text = message.getText();
        return text == null ? "" : text;
    }

    private static boolean requiresStructuredOutput(NodeDef node) {
        Object configuredFlag = node.get("structOutputEnabled");
        if (configuredFlag != null) {
            return configuredFlag instanceof Boolean booleanFlag
                    ? booleanFlag
                    : Boolean.parseBoolean(String.valueOf(configuredFlag));
        }
        return node.get("structOutput") != null;
    }

    private static String extractContent(ChatResponse response) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null) return "";
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private static void attachUsage(NodeDef node, ChatResponse response, NodeResult result) {
        if (response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) return;
        long tokens = response.getMetadata().getUsage().getTotalTokens();
        String cost = null;
        String configuredRate = node.getString("costPer1kTokens");
        if (configuredRate != null && tokens > 0) {
            try {
                cost = new java.math.BigDecimal(configuredRate)
                        .multiply(java.math.BigDecimal.valueOf(tokens))
                        .divide(java.math.BigDecimal.valueOf(1000), 8, java.math.RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString();
            } catch (NumberFormatException ignored) {
            }
        }
        result.usage(tokens, cost);
    }
}
