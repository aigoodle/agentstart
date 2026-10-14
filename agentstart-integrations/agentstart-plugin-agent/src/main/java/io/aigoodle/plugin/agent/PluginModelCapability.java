package io.aigoodle.plugin.agent;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.plugin.host.PluginHostCapability;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Non-streaming platform model invocation. No provider credential or arbitrary endpoint is accepted. */
public final class PluginModelCapability implements PluginHostCapability {
    private final Supplier<ModelService> models;

    public PluginModelCapability(Supplier<ModelService> models) {
        this.models = models;
    }

    @Override
    public String name() {
        return "model.chat";
    }

    @Override
    public Object execute(ConnectorExecutionContext identity, Map<String, Object> arguments) {
        if (Boolean.TRUE.equals(arguments.get("stream")))
            throw new ConnectorException(
                    "plugin_model_stream_unsupported", "Plugin model.chat supports non-streaming calls");
        Object configured = arguments.get("modelId");
        if (!(configured instanceof String modelId) || modelId.isBlank())
            throw new ConnectorException("plugin_model_input", "modelId is required");
        if (!(arguments.get("messages") instanceof List<?> items) || items.isEmpty() || items.size() > 100)
            throw new ConnectorException("plugin_model_input", "Provide 1 to 100 messages");
        List<Message> messages = new ArrayList<>();
        int length = 0;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> message) || !(message.get("content") instanceof String content))
                throw new ConnectorException("plugin_model_input", "Each message requires text content");
            length += content.length();
            if (length > 200_000) throw new ConnectorException("plugin_model_input", "Messages exceed size limit");
            messages.add(
                    switch (String.valueOf(message.get("role"))) {
                        case "system" -> new SystemMessage(content);
                        case "user" -> new UserMessage(content);
                        case "assistant" -> new AssistantMessage(content);
                        default -> throw new ConnectorException("plugin_model_input", "Unsupported message role");
                    });
        }
        var options = ChatOptions.builder();
        if (arguments.containsKey("temperature")) {
            if (!(arguments.get("temperature") instanceof Number value)
                    || !Double.isFinite(value.doubleValue())
                    || value.doubleValue() < 0
                    || value.doubleValue() > 2)
                throw new ConnectorException("plugin_model_input", "temperature must be between 0 and 2");
            options.temperature(value.doubleValue());
        }
        if (arguments.containsKey("maxTokens")) {
            if (!(arguments.get("maxTokens") instanceof Number value)
                    || value.doubleValue() != value.intValue()
                    || value.intValue() < 1
                    || value.intValue() > 32768)
                throw new ConnectorException("plugin_model_input", "maxTokens must be an integer between 1 and 32768");
            options.maxTokens(value.intValue());
        }
        var service = models.get();
        var entity = service.require(identity.tenantId(), modelId);
        if (entity.getModelType() != ModelType.LLM || !Boolean.TRUE.equals(entity.getEnabled()))
            throw new ConnectorException("plugin_model_unavailable", "Selected model is not an enabled LLM");
        var previous = UserContextHolder.get();
        try {
            UserContextHolder.set(CurrentUser.builder()
                    .tenantId(identity.tenantId())
                    .userId(identity.userId())
                    .extra(Map.of("pluginExecutionId", identity.executionId() == null ? "" : identity.executionId()))
                    .build());
            var response =
                    service.getChatModel(identity.tenantId(), modelId).call(new Prompt(messages, options.build()));
            if (response == null || response.getResult() == null)
                throw new ConnectorException("plugin_model_empty", "Model returned no response");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("modelId", modelId);
            result.put("text", response.getResult().getOutput().getText());
            result.put("finishReason", response.getResult().getMetadata().getFinishReason());
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                Map<String, Object> tokens = new LinkedHashMap<>();
                tokens.put("promptTokens", usage.getPromptTokens());
                tokens.put("completionTokens", usage.getCompletionTokens());
                tokens.put("totalTokens", usage.getTotalTokens());
                result.put("usage", tokens);
            }
            return result;
        } finally {
            UserContextHolder.set(previous);
        }
    }
}
