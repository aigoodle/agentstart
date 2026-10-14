package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.common.trigger.ScheduledTaskGateway;
import io.aigoodle.common.trigger.ScheduledTaskGateway.DeletedScheduledTask;
import io.aigoodle.common.trigger.ScheduledTaskGateway.ScheduledTaskCandidate;
import io.aigoodle.common.trigger.ScheduledTaskGateway.ScheduledTaskResult;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.ObjectProvider;

class ScheduleParameterExtractorTest {

    @Test
    void builtInPromptDefinesEveryRequiredScheduleField() {
        String prompt = ScheduleParameterExtractor.promptForTest("Use business days only.");
        assertThat(prompt)
                .contains(
                        "scheduleType",
                        "ONE",
                        "CRON",
                        "runAt",
                        "expression",
                        "CREATE",
                        "UPDATE",
                        "DELETE",
                        "taskName",
                        "updateTriggerId",
                        "deleteTriggerIds",
                        "intent",
                        "data",
                        "yyyy-MM-dd HH:mm:ss",
                        "six fields",
                        "Use business days only.");
    }

    @Test
    void executorExtractsMapCreatesTriggerAndExposesStructuredResult() {
        AtomicReference<List<Message>> capturedMessages = new AtomicReference<>();
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenAnswer(invocation -> {
            capturedMessages.set(invocation.getArgument(0));
            return spec;
        });
        when(spec.call()).thenReturn(call);
        when(call.content())
                .thenReturn(
                        """
                ```json
                {"action":"CREATE","taskName":"明晚八点生成日报","scheduleType":"ONE","runAt":"2099-08-18 20:00:00","intent":"生成日报","data":{"topic":"日报"}}
                ```
                """);

        ModelService modelService = mock(ModelService.class);
        configureModel(modelService, client);
        ScheduledTaskGateway gateway = mock(ScheduledTaskGateway.class);
        ScheduledTaskResult created =
                new ScheduledTaskResult("trigger-1", "明晚八点生成日报", "workflow-2", LocalDateTime.of(2099, 8, 18, 20, 0));
        when(gateway.createTask(any())).thenReturn(created);

        @SuppressWarnings("unchecked")
        ObjectProvider<ScheduledTaskGateway> gatewayProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelService> modelProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(modelProvider.getObject()).thenReturn(modelService);

        NodeDef node = NodeDef.of("schedule", NodeType.SCHEDULE_TRIGGER)
                .with("targetWorkflowId", "workflow-2")
                .with(
                        "model",
                        Map.of(
                                "modelProvider", "openai",
                                "modelName", "gpt-test",
                                "completionParams", Map.of()))
                .with("inputVariableSelector", List.of("llm", "text"))
                .with("targetWorkflowInputs", List.of(Map.of("name", "topic", "type", "string")))
                .with("extractionPrompt", Map.of("text", "Keep the report topic in data."));
        ExecutionContext context = new ExecutionContext();
        context.setTenantId("tenant-1");
        context.setUserId("user-1");
        context.setConversationId("conversation-1");
        context.getPool().put("llm", "text", "明天晚上八点生成日报");

        NodeResult result = new ScheduleTriggerNodeExecutor(gatewayProvider, modelProvider).execute(node, context);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs().get("triggerId")).isEqualTo("trigger-1");
        assertThat(result.getOutputs().get("result"))
                .isEqualTo(Map.of(
                        "action", "CREATE",
                        "taskName", "明晚八点生成日报",
                        "scheduleType", "ONE",
                        "runAt", "2099-08-18 20:00:00",
                        "intent", "生成日报",
                        "data", Map.of("topic", "日报")));
        assertThat(result.getOutputs().get("data")).isEqualTo(Map.of("topic", "日报"));
        assertThat(result.getOutputs().get("intent")).isEqualTo("生成日报");
        assertThat(result.getOutputs().get("action")).isEqualTo("CREATE");
        assertThat(result.getOutputs().get("taskName")).isEqualTo("明晚八点生成日报");
        assertThat(result.getOutputs().get("userId")).isEqualTo("user-1");
        assertThat(result.getOutputs().get("tenantId")).isEqualTo("tenant-1");
        verify(gateway)
                .createTask(argThat(request -> "明晚八点生成日报".equals(request.name())
                        && "user-1".equals(request.userId())
                        && "tenant-1".equals(request.tenantId())));
        assertThat(capturedMessages.get()).hasSize(2);
        assertThat(((SystemMessage) capturedMessages.get().get(0)).getText())
                .contains(
                        "Current server date/time",
                        "Keep the report topic in data.",
                        "Target workflow START-node input definitions",
                        "topic");
        assertThat(capturedMessages.get().get(1).getText()).isEqualTo("明天晚上八点生成日报");
    }

    @Test
    void executorUsesOneModelCallToSelectAndDeleteOwnedSchedules() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        AtomicReference<List<Message>> messages = new AtomicReference<>();
        when(client.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenAnswer(invocation -> {
            messages.set(invocation.getArgument(0));
            return spec;
        });
        when(spec.call()).thenReturn(call);
        when(call.content())
                .thenReturn(
                        """
                {"action":"DELETE","intent":"取消每日报告","deleteTriggerIds":["trigger-daily"]}
                """);
        ModelService modelService = mock(ModelService.class);
        configureModel(modelService, client);

        ScheduledTaskCandidate candidate = new ScheduledTaskCandidate(
                "trigger-daily",
                "每天早上八点生成日报",
                "CRON",
                null,
                "0 0 8 * * *",
                null,
                null,
                true,
                "workflow-2",
                Map.of("topic", "日报"));
        ScheduledTaskGateway gateway = mock(ScheduledTaskGateway.class);
        when(gateway.listUserTasks("tenant-1", "user-1")).thenReturn(List.of(candidate));
        when(gateway.deleteOwnedTasks("tenant-1", "user-1", List.of("trigger-daily")))
                .thenReturn(List.of(new DeletedScheduledTask("trigger-daily", "每天早上八点生成日报")));

        @SuppressWarnings("unchecked")
        ObjectProvider<ScheduledTaskGateway> gatewayProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelService> modelProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(modelProvider.getObject()).thenReturn(modelService);
        NodeDef node = NodeDef.of("schedule", NodeType.SCHEDULE_TRIGGER)
                .with("model", Map.of("modelProvider", "openai", "modelName", "gpt-test"))
                .with("inputVariableSelector", List.of("llm", "text"));
        ExecutionContext context = new ExecutionContext();
        context.setTenantId("tenant-1");
        context.setUserId("user-1");
        context.getPool().put("llm", "text", "把我每天生成日报的任务取消掉");

        NodeResult result = new ScheduleTriggerNodeExecutor(gatewayProvider, modelProvider).execute(node, context);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs().get("action")).isEqualTo("DELETE");
        assertThat(result.getOutputs().get("deletedCount")).isEqualTo(1);
        assertThat(result.getOutputs().get("deletedTriggers"))
                .isEqualTo(List.of(Map.of("id", "trigger-daily", "name", "每天早上八点生成日报")));
        assertThat(((SystemMessage) messages.get().get(0)).getText())
                .contains("trigger-daily", "每天早上八点生成日报", "deleteTriggerIds");
        verify(gateway, never()).createTask(any());
        verify(gateway).deleteOwnedTasks("tenant-1", "user-1", List.of("trigger-daily"));
    }

    @Test
    void reportsUnavailableCapabilityWhenTriggerModuleIsNotIntegrated() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ScheduledTaskGateway> gatewayProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelService> modelProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(null);

        NodeResult result = new ScheduleTriggerNodeExecutor(gatewayProvider, modelProvider)
                .execute(NodeDef.of("schedule", NodeType.SCHEDULE_TRIGGER), new ExecutionContext());

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).contains("agentstart-trigger");
    }

    @Test
    void missingUserIdentityReturnsAConsumableResultInsteadOfFailingTheWorkflow() {
        ScheduledTaskGateway gateway = mock(ScheduledTaskGateway.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ScheduledTaskGateway> gatewayProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelService> modelProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        NodeDef node =
                NodeDef.of("schedule", NodeType.SCHEDULE_TRIGGER).with("inputVariableSelector", List.of("llm", "text"));
        ExecutionContext context = new ExecutionContext();
        context.setTenantId("tenant-1");

        NodeResult result = new ScheduleTriggerNodeExecutor(gatewayProvider, modelProvider).execute(node, context);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs())
                .containsEntry("success", false)
                .containsEntry("action", "REJECTED")
                .containsEntry("errorCode", "AUTHENTICATION_REQUIRED")
                .containsEntry("tenantId", "tenant-1");
        assertThat(result.getOutputs().get("message")).isEqualTo("定时任务管理需要用户先完成登录认证");
        verify(gateway, never()).listUserTasks(any(), any());
    }

    @Test
    void rejectsHallucinatedDeleteIdsBeforeCallingTheService() {
        Map<String, Object> decision = new java.util.LinkedHashMap<>(Map.of(
                "action", "DELETE",
                "intent", "删除日报任务",
                "deleteTriggerIds", List.of("another-users-trigger")));

        assertThatThrownBy(() -> ScheduleParameterExtractor.validate(
                        decision, List.of(Map.of("id", "owned-trigger", "name", "我的日报"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown schedule id");
    }

    @Test
    void executorUpdatesAnOwnedScheduleSelectedByTheModel() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content())
                .thenReturn(
                        """
                {"action":"UPDATE","updateTriggerId":"trigger-daily","taskName":"每天九点生成日报","intent":"把日报改到九点","scheduleType":"CRON","expression":"0 0 9 * * *","data":{}}
                """);
        ModelService modelService = mock(ModelService.class);
        configureModel(modelService, client);
        ScheduledTaskGateway gateway = mock(ScheduledTaskGateway.class);
        when(gateway.listUserTasks("tenant-1", "user-1"))
                .thenReturn(List.of(new ScheduledTaskCandidate(
                        "trigger-daily",
                        "每天八点生成日报",
                        "CRON",
                        null,
                        "0 0 8 * * *",
                        null,
                        null,
                        true,
                        "workflow-2",
                        Map.of("topic", "日报"))));
        when(gateway.updateOwnedTask(any()))
                .thenReturn(new ScheduledTaskResult("trigger-daily", "每天九点生成日报", "workflow-2", null));
        @SuppressWarnings("unchecked")
        ObjectProvider<ScheduledTaskGateway> gatewayProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelService> modelProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(modelProvider.getObject()).thenReturn(modelService);
        NodeDef node = NodeDef.of("schedule", NodeType.SCHEDULE_TRIGGER)
                .with("model", Map.of("modelProvider", "openai", "modelName", "gpt-test"))
                .with("inputVariableSelector", List.of("llm", "text"));
        ExecutionContext context = new ExecutionContext();
        context.setTenantId("tenant-1");
        context.setUserId("user-1");
        context.getPool().put("llm", "text", "把每天八点的日报改到九点");

        NodeResult result = new ScheduleTriggerNodeExecutor(gatewayProvider, modelProvider).execute(node, context);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOutputs())
                .containsEntry("action", "UPDATE")
                .containsEntry("triggerId", "trigger-daily")
                .containsEntry("data", Map.of("topic", "日报"));
        verify(gateway)
                .updateOwnedTask(argThat(command -> command.taskId().equals("trigger-daily")
                        && command.config().get("expression").equals("0 0 9 * * *")
                        && command.config().get("data").equals(Map.of("topic", "日报"))));
    }

    private static void configureModel(ModelService modelService, ChatClient client) {
        ModelEntity model = new ModelEntity();
        model.setId("materialized-model");
        when(modelService.findOrMaterialize("tenant-1", "openai", "gpt-test", ModelType.LLM))
                .thenReturn(model);
        when(modelService.getChatClient(anyString(), eq("materialized-model"))).thenReturn(client);
    }
}
