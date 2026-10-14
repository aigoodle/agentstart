package io.aigoodle.plugin.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class PluginModelCapabilityTest {
    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    private ConnectorExecutionContext identity() {
        return new ConnectorExecutionContext("exec-1", "tenant-a", "user-a", null, null, null, null, Map.of());
    }

    private Map<String, Object> input() {
        return Map.of(
                "modelId",
                "model-1",
                "tenantId",
                "forged-tenant",
                "messages",
                List.of(
                        Map.of("role", "system", "content", "Write a brief"),
                        Map.of("role", "user", "content", "Product")),
                "maxTokens",
                100,
                "temperature",
                0.5);
    }

    @Test
    void usesTenantBoundModelAndRestoresThreadIdentity() {
        var models = mock(ModelService.class);
        var model = mock(ChatModel.class);
        var entity = new ModelEntity();
        entity.setModelType(ModelType.LLM);
        entity.setEnabled(true);
        when(models.require("tenant-a", "model-1")).thenReturn(entity);
        when(models.getChatModel("tenant-a", "model-1")).thenReturn(model);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            assertThat(UserContextHolder.currentTenantId()).isEqualTo("tenant-a");
            assertThat(UserContextHolder.currentUserId()).isEqualTo("user-a");
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getInstructions()).hasSize(2);
            assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(100);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Video brief"))));
        });
        var previous = CurrentUser.builder().tenantId("previous").build();
        UserContextHolder.set(previous);
        Object output = new PluginModelCapability(() -> models).execute(identity(), input());
        assertThat(((Map<?, ?>) output).get("text")).isEqualTo("Video brief");
        assertThat(UserContextHolder.get()).isSameAs(previous);
        verify(models, never()).require(eq("forged-tenant"), anyString());
    }

    @Test
    void rejectsStreamingAndDisabledModelsBeforeCallingProvider() {
        var models = mock(ModelService.class);
        var capability = new PluginModelCapability(() -> models);
        assertThatThrownBy(() -> capability.execute(identity(), Map.of("stream", true)))
                .isInstanceOf(ConnectorException.class);
        var entity = new ModelEntity();
        entity.setModelType(ModelType.LLM);
        entity.setEnabled(false);
        when(models.require("tenant-a", "model-1")).thenReturn(entity);
        assertThatThrownBy(() -> capability.execute(identity(), input())).hasMessageContaining("enabled LLM");
        verify(models, never()).getChatModel(anyString(), anyString());
    }

    @Test
    void providerFailureStillRestoresIdentity() {
        var models = mock(ModelService.class);
        var entity = new ModelEntity();
        entity.setModelType(ModelType.LLM);
        entity.setEnabled(true);
        when(models.require("tenant-a", "model-1")).thenReturn(entity);
        when(models.getChatModel("tenant-a", "model-1")).thenThrow(new IllegalStateException("provider down"));
        assertThatThrownBy(() -> new PluginModelCapability(() -> models).execute(identity(), input()))
                .hasMessage("provider down");
        assertThat(UserContextHolder.get()).isNull();
    }
}
