package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class NodeModelReferenceTest {

    @Test
    void readsTheCurrentNestedDesignerShapeAndItsSettings() {
        NodeDef node = NodeDef.of("llm", NodeType.LLM)
                .with(
                        "model",
                        Map.of(
                                "modelProvider", "qwen",
                                "modelName", "qwen-plus",
                                "completionParams", Map.of("temperature", 0.4)));

        NodeModelReference reference = NodeModelReference.from(node);

        assertThat(reference.provider()).isEqualTo("qwen");
        assertThat(reference.modelName()).isEqualTo("qwen-plus");
        assertThat(reference.entityId()).isNull();
        assertThat(reference.completionSettings()).containsEntry("temperature", 0.4);
    }

    @Test
    void acceptsNestedAliasesAndLegacyEntityIds() {
        NodeModelReference aliases = NodeModelReference.from(NodeDef.of("llm", NodeType.LLM)
                .with(
                        "model",
                        Map.of(
                                "providerName", "deepseek",
                                "model", "deepseek-chat")));
        NodeModelReference legacy =
                NodeModelReference.from(NodeDef.of("llm", NodeType.LLM).with("modelId", "database-row-id"));

        assertThat(aliases.provider()).isEqualTo("deepseek");
        assertThat(aliases.modelName()).isEqualTo("deepseek-chat");
        assertThat(legacy.entityId()).isEqualTo("database-row-id");
    }

    @Test
    void resolvesTheCompositeIdReturnedByTheWebModelCatalog() {
        ModelService modelService = mock(ModelService.class);
        ModelEntity materializedModel = new ModelEntity();
        materializedModel.setId("materialized-id");
        ChatClient expectedClient = mock(ChatClient.class);
        when(modelService.findOrMaterialize("tenant-a", "openai", "gpt-4o", ModelType.LLM))
                .thenReturn(materializedModel);
        when(modelService.getChatClient("tenant-a", "materialized-id")).thenReturn(expectedClient);
        ExecutionContext context = new ExecutionContext();
        context.setTenantId("tenant-a");
        NodeDef node = NodeDef.of("llm", NodeType.LLM).with("modelId", "openai::gpt-4o::LLM");

        ChatClient resolvedClient = NodeModelResolver.resolve(node, context, modelService);

        assertThat(resolvedClient).isSameAs(expectedClient);
        verify(modelService).findOrMaterialize("tenant-a", "openai", "gpt-4o", ModelType.LLM);
    }

    @Test
    void defaultsBlankWorkflowTenantsDuringMaterialization() {
        ModelService modelService = mock(ModelService.class);
        ModelEntity materializedModel = new ModelEntity();
        materializedModel.setId("model-id");
        when(modelService.findOrMaterialize("default", "zhipu", "glm-4", ModelType.LLM))
                .thenReturn(materializedModel);
        NodeDef node =
                NodeDef.of("llm", NodeType.LLM).with("modelProvider", "zhipu").with("modelName", "glm-4");

        NodeModelResolver.resolve(node, new ExecutionContext(), modelService);

        verify(modelService).findOrMaterialize("default", "zhipu", "glm-4", ModelType.LLM);
    }

    @Test
    void fallsBackToCurrentUserTenantWhenExecutionContextHasNoTenant() {
        ModelService modelService = mock(ModelService.class);
        ModelEntity materializedModel = new ModelEntity();
        materializedModel.setId("model-id");
        when(modelService.findOrMaterialize("tenant-from-user", "qwen", "qwen-plus", ModelType.LLM))
                .thenReturn(materializedModel);
        NodeDef node =
                NodeDef.of("llm", NodeType.LLM).with("modelProvider", "qwen").with("modelName", "qwen-plus");
        CurrentUser user = CurrentUser.builder()
                .userId("user-1")
                .tenantId("tenant-from-user")
                .build();

        UserContextHolder.runAs(user, () -> NodeModelResolver.resolve(node, new ExecutionContext(), modelService));

        verify(modelService).findOrMaterialize("tenant-from-user", "qwen", "qwen-plus", ModelType.LLM);
    }
}
