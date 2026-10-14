package io.aigoodle.workflow.node.builtin;

import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.PromptTemplateService;
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
 * Classifies the input into one of several classes using an LLM, then branches on the
 * chosen class id (the outgoing handle = class id). Config:
 * {@code modelProvider} + {@code modelName}, {@code classes}
 * (list of {@code {id,name}}), {@code query} (template).
 */
public class QuestionClassifierNodeExecutor implements NodeExecutor {

    private final ModelService modelService;
    private final ClassifierPromptBuilder promptBuilder;
    private final MemoryManager memoryManager;

    public QuestionClassifierNodeExecutor(ModelService modelService) {
        this(modelService, null, null);
    }

    public QuestionClassifierNodeExecutor(ModelService modelService, PromptTemplateService promptTemplateService) {
        this(modelService, promptTemplateService, null);
    }

    public QuestionClassifierNodeExecutor(
            ModelService modelService, PromptTemplateService promptTemplateService, MemoryManager memoryManager) {
        this.modelService = modelService;
        this.promptBuilder = new ClassifierPromptBuilder(promptTemplateService);
        this.memoryManager = memoryManager;
    }

    @Override
    public NodeType type() {
        return NodeType.QUESTION_CLASSIFIER;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        ChatClient chatClient;
        try {
            chatClient = NodeModelResolver.resolve(node, context, modelService);
        } catch (IllegalArgumentException exception) {
            return NodeResult.failure("Question classifier requires modelProvider + modelName");
        }
        ClassifierCategorySet categorySet = ClassifierCategorySet.from(node.getMapList("classes"));
        if (categorySet.isEmpty()) {
            return NodeResult.failure("Question classifier requires 'classes'");
        }
        String query = VariableResolver.render(node.getString("query", "{{#sys.query#}}"), context.getPool());

        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        messages.add(new SystemMessage(context.withResourceTenant(() -> promptBuilder.build(node, categorySet))));
        messages.addAll(WorkflowMemoryMessages.load(memoryManager, node, context));
        messages.add(new UserMessage(query));
        ChatClient.ChatClientRequestSpec request = chatClient.prompt().messages(messages);
        ChatOptions nodeOptions = NodeModelResolver.perNodeOptions(node);
        if (nodeOptions != null) {
            request = request.options(nodeOptions.mutate());
        }
        String modelResponse = request.call().content();
        ClassifierCategorySet.ClassifierCategory selectedCategory = categorySet.match(modelResponse);
        return NodeResult.empty()
                .output("classId", selectedCategory.id())
                .output("className", selectedCategory.name())
                .handle(selectedCategory.id());
    }
}
