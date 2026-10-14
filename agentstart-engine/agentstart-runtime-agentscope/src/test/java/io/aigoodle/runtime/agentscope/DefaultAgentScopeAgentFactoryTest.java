package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.runtime.ModelInstance;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.skill.service.SkillResolution;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class DefaultAgentScopeAgentFactoryTest {
    @org.junit.jupiter.api.Test
    void delegationUsesScopedGatewayAndCapturedCaller() {
        var calls = new AtomicInteger();
        var gatewayCalls = new AtomicInteger();
        var childCalls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                var output = calls.incrementAndGet() == 1
                        ? AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "delegate-call", "function", "delegate_to_child", "{\"input\":\"subtask\"}")))
                                .build()
                        : new AssistantMessage("delegation complete");
                return Flux.just(new ChatResponse(List.of(new Generation(output))));
            }
        };
        var models = mock(ModelService.class);
        when(models.getDefaultInstance("default", ModelType.LLM))
                .thenReturn(ModelInstance.forChat(
                        "offline",
                        ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(ModelType.LLM)
                                .build(),
                        model));
        var registry = new ToolRegistry(
                List.of(),
                List.of(),
                (tool, input, identity) -> {
                    assertEquals("delegate-owner", identity.ownerId());
                    assertEquals("delegate-owner", UserContextHolder.currentUserId());
                    gatewayCalls.incrementAndGet();
                    return tool.execute(input);
                },
                null);
        var factory = new DefaultAgentScopeAgentFactory(
                models,
                registry,
                io.aigoodle.skill.service.SkillResolver.none(),
                new io.agentscope.core.state.InMemoryAgentStateStore(),
                definition -> {
                    assertEquals(List.of("child"), definition.getDelegateAgentIds());
                    return List.of(new io.aigoodle.agent.multiagent.AgentDelegationTool(
                            "delegate_to_child", "Child", "child", (id, request) -> {
                                assertEquals("child", id);
                                assertEquals("subtask", request.getQuery());
                                assertEquals("delegate-owner", UserContextHolder.currentUserId());
                                childCalls.incrementAndGet();
                                return AgentResponse.forConversation("child-session")
                                        .complete("child answer");
                            }));
                });
        var runtime = new AgentScopeRuntime(Map.of("default", factory));
        var response = UserContextHolder.callAs(
                CurrentUser.builder()
                        .tenantId("default")
                        .userId("delegate-owner")
                        .build(),
                () -> runtime.run(
                        AgentDefinition.builder()
                                .id("parent")
                                .delegateAgentIds(List.of("child"))
                                .build(),
                        AgentRequest.of("delegate")));
        assertEquals("delegation complete", response.getText(), response.getError());
        assertEquals(1, gatewayCalls.get());
        assertEquals(1, childCalls.get());
        assertTrue(registry.names().isEmpty());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "true,0,6",
        "false,0,6",
        "true,1,6",
        "false,1,6",
        "true,2,6",
        "false,2,6",
        "true,0,1",
        "false,0,1"
    })
    void sdkAsksBeforeExecutingAGovernedTool(boolean approved, int modelBudget, int maximumIterations) {
        var decision = approved
                ? io.aigoodle.agent.runtime.AgentResumeCommand.Decision.APPROVE
                : io.aigoodle.agent.runtime.AgentResumeCommand.Decision.DENY;
        var modelCalls = new AtomicInteger();
        var toolCalls = new AtomicInteger();
        var approvals = new java.util.ArrayList<AgentResponse.PendingApproval>();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                if (modelCalls.incrementAndGet() > 1) {
                    return Flux.just(
                            new ChatResponse(List.of(new Generation(new AssistantMessage("approved answer")))));
                }
                return Flux.just(new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                new AssistantMessage.ToolCall("approval-call", "function", "echo", "{\"value\":42}")))
                        .build()))));
            }
        };
        var models = mock(ModelService.class);
        when(models.getDefaultInstance("default", ModelType.LLM))
                .thenReturn(ModelInstance.forChat(
                        "offline",
                        ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(ModelType.LLM)
                                .build(),
                        model));
        ToolDefinition echo = new ToolDefinition() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "Echo";
            }

            public Object execute(Map<String, Object> args) {
                toolCalls.incrementAndGet();
                return args.get("value");
            }
        };
        var factory = new DefaultAgentScopeAgentFactory(
                models, new ToolRegistry(List.of(echo), List.of()), io.aigoodle.skill.service.SkillResolver.none());
        var context = io.agentscope.core.agent.RuntimeContext.builder()
                .userId("user")
                .sessionId("session")
                .put("tenantId", "default")
                .put("runId", "approval-run")
                .build();
        try (var agent = factory.create(
                AgentDefinition.builder()
                        .approvalRequiredTools(java.util.Set.of("echo"))
                        .build(),
                AgentRequest.of("use echo"),
                context)) {
            var events = agent.streamEvents("use echo", context).collectList().block();
            var confirmation = events.stream()
                    .filter(io.agentscope.core.event.RequireUserConfirmEvent.class::isInstance)
                    .map(io.agentscope.core.event.RequireUserConfirmEvent.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertEquals("approval-call", confirmation.getToolCalls().getFirst().getId());
            assertEquals(
                    Map.of("value", 42), confirmation.getToolCalls().getFirst().getInput());
            assertEquals(0, toolCalls.get());
            approvals.addAll(AgentScopeApprovalBridge.pending(confirmation));
        }
        var resumeMessage = AgentScopeApprovalBridge.confirmation(
                approvals, new io.aigoodle.agent.runtime.AgentResumeCommand("approval-call", decision));
        try (var resumed = factory.create(
                AgentDefinition.builder()
                        .approvalRequiredTools(java.util.Set.of("echo"))
                        .build(),
                AgentRequest.of("use echo"),
                context)) {
            var events =
                    resumed.streamEvents(resumeMessage, context).collectList().block();
            assertEquals(
                    approved ? 1 : 0,
                    toolCalls.get(),
                    events.stream()
                            .filter(io.agentscope.core.event.ToolResultTextDeltaEvent.class::isInstance)
                            .map(io.agentscope.core.event.ToolResultTextDeltaEvent.class::cast)
                            .map(io.agentscope.core.event.ToolResultTextDeltaEvent::getDelta)
                            .collect(java.util.stream.Collectors.joining()));
            assertTrue(events.stream()
                    .filter(io.agentscope.core.event.AgentResultEvent.class::isInstance)
                    .map(io.agentscope.core.event.AgentResultEvent.class::cast)
                    .anyMatch(
                            event -> "approved answer".equals(event.getResult().getTextContent())));
        }
        modelCalls.set(0);
        toolCalls.set(0);
        var runtime = new AgentScopeRuntime(Map.of("default", factory));
        var paused = runtime.run(
                AgentDefinition.builder()
                        .id("public-approval-agent")
                        .maxIterations(maximumIterations)
                        .maxModelCalls(modelBudget)
                        .approvalRequiredTools(java.util.Set.of("echo"))
                        .build(),
                AgentRequest.of("use echo"));
        assertEquals(AgentResponse.Status.AWAITING_APPROVAL, paused.getStatus(), paused.getError());
        assertTrue(paused.getSteps().stream()
                .anyMatch(step -> step.getKind() == io.aigoodle.agent.api.AgentStep.Kind.APPROVAL));
        assertFalse(paused.getSteps().stream()
                .anyMatch(step -> step.getKind() == io.aigoodle.agent.api.AgentStep.Kind.FINAL));
        assertEquals(
                io.aigoodle.agent.runtime.AgentRunStatus.WAITING_APPROVAL,
                runtime.findRun(paused.getRunId()).orElseThrow().status());
        assertEquals(0, toolCalls.get());
        UserContextHolder.runAs(
                CurrentUser.builder().tenantId("default").userId("other-owner").build(),
                () -> assertThrows(
                        SecurityException.class,
                        () -> runtime.resume(
                                paused.getRunId(),
                                new io.aigoodle.agent.runtime.AgentResumeCommand("approval-call", decision))));
        var completed = runtime.resume(
                paused.getRunId(), new io.aigoodle.agent.runtime.AgentResumeCommand("approval-call", decision));
        assertEquals(
                maximumIterations == 1
                        ? AgentResponse.Status.MAX_ITERATIONS
                        : modelBudget == 1 ? AgentResponse.Status.FAILED : AgentResponse.Status.COMPLETED,
                completed.getStatus(),
                completed.getError());
        if (modelBudget == 1) assertTrue(completed.getError().contains("budget exceeded"), completed.getError());
        assertEquals(paused.getRunId(), completed.getRunId());
        assertEquals(approved ? 1 : 0, toolCalls.get());
    }

    @org.junit.jupiter.api.Test
    void simultaneousSessionWritersReportConflictInsteadOfSilentlyOverwriting() throws Exception {
        var reached = new java.util.concurrent.CountDownLatch(2);
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> {
                    reached.countDown();
                    try {
                        if (!reached.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            return Flux.error(new IllegalStateException("Concurrent calls did not reach model"));
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return Flux.error(exception);
                    }
                    return Flux.just(
                            new ChatResponse(List.of(new Generation(new AssistantMessage("parallel answer")))));
                });
            }
        };
        ModelService models = mock(ModelService.class);
        when(models.getDefaultInstance("default", ModelType.LLM))
                .thenReturn(ModelInstance.forChat(
                        "offline",
                        ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(ModelType.LLM)
                                .build(),
                        model));
        var factory = new DefaultAgentScopeAgentFactory(
                models, new ToolRegistry(List.of(), List.of()), io.aigoodle.skill.service.SkillResolver.none());
        var runtime = new AgentScopeRuntime(Map.of("default", factory));
        var definition = AgentDefinition.builder().id("concurrent-agent").build();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> runtime.run(
                    definition,
                    AgentRequest.builder()
                            .query("first")
                            .conversationId("concurrent-session")
                            .build()));
            var second = executor.submit(() -> runtime.run(
                    definition,
                    AgentRequest.builder()
                            .query("second")
                            .conversationId("concurrent-session")
                            .build()));
            var results = List.of(
                    first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(AgentResponse::isCompleted).count());
            var failed = results.stream()
                    .filter(result -> !result.isCompleted())
                    .findFirst()
                    .orElseThrow();
            assertTrue(failed.getError().contains("Concurrent modification of session state"), failed.getError());
        }
    }

    @org.junit.jupiter.api.Test
    void subsequentCallRecallsConversationWhileMemoryDisabledDoesNot() {
        var prompts = new java.util.ArrayList<Prompt>();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                prompts.add(prompt);
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("remembered answer")))));
            }
        };
        ModelService models = mock(ModelService.class);
        when(models.getDefaultInstance("default", ModelType.LLM))
                .thenReturn(ModelInstance.forChat(
                        "offline",
                        ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(ModelType.LLM)
                                .build(),
                        model));
        var factory = new DefaultAgentScopeAgentFactory(
                models, new ToolRegistry(List.of(), List.of()), io.aigoodle.skill.service.SkillResolver.none());
        var runtime = new AgentScopeRuntime(Map.of("default", factory));
        var definition = AgentDefinition.builder().id("memory-agent").build();
        runtime.run(
                definition,
                AgentRequest.builder()
                        .query("first question")
                        .conversationId("shared-session")
                        .build());
        runtime.run(
                definition,
                AgentRequest.builder()
                        .query("second question")
                        .conversationId("shared-session")
                        .build());
        assertTrue(prompts.get(1).getInstructions().stream()
                .anyMatch(message -> message.getText().contains("first question")));
        assertTrue(prompts.get(1).getInstructions().stream()
                .anyMatch(message -> message.getText().contains("remembered answer")));
        runtime.run(
                definition.toBuilder().memoryEnabled(false).build(),
                AgentRequest.builder()
                        .query("isolated question")
                        .conversationId("shared-session")
                        .build());
        assertFalse(prompts.get(2).getInstructions().stream()
                .anyMatch(message -> message.getText().contains("first question")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void realSdkFactoryResolvesTenantModelSkillsAndGovernedToolWithinBudget(int modelBudget) {
        var caller =
                CurrentUser.builder().tenantId("tenant-a").userId("owner-a").build();
        var modelCalls = new AtomicInteger();
        var toolCalls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                assertTrue(prompt.getInstructions().getFirst().getText().contains("skill instructions"));
                if (modelCalls.incrementAndGet() == 1)
                    return Flux.just(new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(
                                    new AssistantMessage.ToolCall("call-1", "function", "echo", "{\"value\":42}")))
                            .build()))));
                var result = prompt.getInstructions().stream()
                        .filter(ToolResponseMessage.class::isInstance)
                        .map(ToolResponseMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
                assertEquals("42", result.getResponses().getFirst().responseData());
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("answer 42")))));
            }
        };
        ModelService models = mock(ModelService.class);
        when(models.getDefaultInstance("tenant-a", ModelType.LLM))
                .thenReturn(ModelInstance.forChat(
                        "model-a",
                        ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(ModelType.LLM)
                                .build(),
                        model));
        ToolDefinition echo = new ToolDefinition() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "Echo a value";
            }

            public Object execute(Map<String, Object> input) {
                throw new AssertionError("Must use gateway");
            }
        };
        var registry = new ToolRegistry(
                List.of(echo),
                List.of(),
                (tool, args, context) -> {
                    assertSame(caller, UserContextHolder.get());
                    assertEquals("tenant-a", context.tenantId());
                    assertEquals("owner-a", context.ownerId());
                    assertEquals("session-a", context.conversationId());
                    assertNotNull(context.executionId());
                    toolCalls.incrementAndGet();
                    return args.get("value");
                },
                null);
        var factory = new DefaultAgentScopeAgentFactory(models, registry, (tenant, ids) -> {
            assertEquals("tenant-a", tenant);
            assertEquals(List.of("skill-a"), ids);
            return new SkillResolution("skill instructions", List.of("echo"));
        });
        var runtime = new AgentScopeRuntime(Map.of("agentScopeAgentFactory", factory));
        var response = UserContextHolder.callAs(
                caller,
                () -> runtime.run(
                        AgentDefinition.builder()
                                .tenantId("tenant-a")
                                .skillIds(List.of("skill-a"))
                                .maxModelCalls(modelBudget)
                                .build(),
                        AgentRequest.builder()
                                .query("Calculate")
                                .conversationId("session-a")
                                .build(),
                        null,
                        null));
        if (modelBudget == 1) {
            assertEquals(AgentResponse.Status.FAILED, response.getStatus());
            assertTrue(response.getError().contains("budget exceeded"), response.getError());
            assertEquals(1, modelCalls.get());
            UserContextHolder.runAs(
                    caller,
                    () -> assertEquals(
                            io.aigoodle.agent.runtime.AgentRunStatus.FAILED,
                            runtime.findRun(response.getRunId()).orElseThrow().status()));
        } else {
            assertEquals(AgentResponse.Status.COMPLETED, response.getStatus(), response.getError());
            assertEquals("answer 42", response.getText());
            assertEquals(2, modelCalls.get());
        }
        assertEquals(1, toolCalls.get());
        assertTrue(response.getSteps().stream()
                .anyMatch(step -> "echo".equals(step.getAction())
                        && step.getActionInput() != null
                        && step.getActionInput().contains("42")));
        assertTrue(response.getSteps().stream().anyMatch(step -> "42".equals(step.getObservation())));
        verify(models).getDefaultInstance("tenant-a", ModelType.LLM);
    }
}
