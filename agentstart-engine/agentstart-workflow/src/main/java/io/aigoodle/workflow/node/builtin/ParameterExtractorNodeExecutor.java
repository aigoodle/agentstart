package io.aigoodle.workflow.node.builtin;

import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.ArrayList;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Asks an LLM to pull structured fields out of a free-form input, mirroring Dify's
 * parameter-extractor node.
 * <p>
 * Config: {@code modelProvider} + {@code modelName} (required), {@code query}
 * (template), {@code parameters}
 * (list of {@code {name, type, description, required}}),
 * and an optional {@code systemPrompt} — either a plain string or an
 * object shaped like {@code {text: "..."}} (to match the frontend prompt
 * editor). When present, it is rendered as a variable template and prepended
 * to the fixed JSON-schema instruction. Each declared parameter is emitted as
 * an output; missing values default to {@code null}. Extraction failures
 * do not fail the node — they yield empty outputs so the graph can continue.
 * <p>
 * Honours per-node {@code model.completionParams} the same way the LLM node
 * does (via {@link NodeModelResolver#perNodeOptions}), so
 * {@code enable_thinking=false} / {@code thinkingMode=disabled} disables the
 * model's reasoning preamble here too — otherwise the extractor would burn
 * seconds on a reasoning trace it never uses before emitting the JSON.
 */
public class ParameterExtractorNodeExecutor implements NodeExecutor {

    private final ModelService modelService;
    private final MemoryManager memoryManager;

    public ParameterExtractorNodeExecutor(ModelService modelService) {
        this(modelService, null);
    }

    public ParameterExtractorNodeExecutor(ModelService modelService, MemoryManager memoryManager) {
        this.modelService = modelService;
        this.memoryManager = memoryManager;
    }

    @Override
    public NodeType type() {
        return NodeType.PARAMETER_EXTRACTOR;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        ChatClient chatClient;
        try {
            chatClient = NodeModelResolver.resolve(node, context, modelService);
        } catch (IllegalArgumentException exception) {
            return NodeResult.failure("Parameter extractor requires modelProvider + modelName");
        }
        ExtractionParameterSet parameterSet = ExtractionParameterSet.from(node.getMapList("parameters"));
        if (parameterSet.isEmpty()) {
            return NodeResult.failure("Parameter extractor requires 'parameters'");
        }
        String query = VariableResolver.render(node.getString("query", "{{#sys.query#}}"), context.getPool());
        String systemPrompt = ParameterExtractionPromptBuilder.build(node, context, parameterSet);

        try {
            String modelResponse = invokeModel(chatClient, node, context, systemPrompt, query);
            return parameterSet.resultFrom(modelResponse);
        } catch (Exception extractionFailure) {
            // Extraction is non-fatal; every declared output remains addressable as null.
            return parameterSet.failedResult(extractionFailure);
        }
    }

    private String invokeModel(
            ChatClient chatClient, NodeDef node, ExecutionContext context, String systemPrompt, String query) {
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(WorkflowMemoryMessages.load(memoryManager, node, context));
        messages.add(new UserMessage(query));
        ChatClient.ChatClientRequestSpec request = chatClient.prompt().messages(messages);
        ChatOptions nodeOptions = NodeModelResolver.perNodeOptions(node);
        if (nodeOptions != null) {
            request = request.options(nodeOptions.mutate());
        }
        return request.call().content();
    }
}
