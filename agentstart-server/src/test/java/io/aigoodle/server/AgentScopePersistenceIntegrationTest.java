package io.aigoodle.server;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.runtime.AgentRunStatus;
import io.aigoodle.agent.runtime.AgentRunStore;
import io.aigoodle.agent.runtime.JdbcAgentRunStore;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.runtime.agentscope.AgentScopeAgentFactory;
import io.aigoodle.runtime.agentscope.AgentScopeRuntime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux;

@SpringBootTest(
        classes = GoodleServerApplication.class,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:agentscopepersist;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.continue-on-error=false",
            "spring-agent.channel.backfill-enabled=false",
            "management.health.redis.enabled=false"
        })
@Import(AgentScopePersistenceIntegrationTest.OfflineFactory.class)
class AgentScopePersistenceIntegrationTest {
    @Autowired
    private AgentScopeRuntime runtime;

    @Autowired
    private AgentRunStore store;

    @Autowired
    private io.agentscope.core.state.AgentStateStore sdkState;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    public record TestState(String text) implements io.agentscope.core.state.State {}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void approvalResumesAfterRuntimeFactoryAndSdkStoreAreRecreated(int approvalRounds) {
        var modelCalls = new java.util.concurrent.atomic.AtomicInteger();
        var toolCalls = new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.ai.chat.model.ChatModel model = new org.springframework.ai.chat.model.ChatModel() {
            public org.springframework.ai.chat.model.ChatResponse call(
                    org.springframework.ai.chat.prompt.Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<org.springframework.ai.chat.model.ChatResponse> stream(
                    org.springframework.ai.chat.prompt.Prompt prompt) {
                int call = modelCalls.incrementAndGet();
                var output = call <= approvalRounds
                        ? org.springframework.ai.chat.messages.AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new org.springframework.ai.chat.messages.AssistantMessage.ToolCall(
                                        "persisted-call-" + call, "function", "echo", "{\"value\":42}")))
                                .build()
                        : new org.springframework.ai.chat.messages.AssistantMessage("restored answer");
                return Flux.just(new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new org.springframework.ai.chat.model.Generation(output))));
            }
        };
        var models = org.mockito.Mockito.mock(io.aigoodle.model.service.ModelService.class);
        org.mockito.Mockito.when(models.getDefaultInstance("approval-tenant", io.aigoodle.model.enums.ModelType.LLM))
                .thenReturn(io.aigoodle.model.runtime.ModelInstance.forChat(
                        "offline",
                        io.aigoodle.model.provider.ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(io.aigoodle.model.enums.ModelType.LLM)
                                .build(),
                        model));
        io.aigoodle.tool.ToolDefinition tool = new io.aigoodle.tool.ToolDefinition() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "Echo";
            }

            public Object execute(java.util.Map<String, Object> arguments) {
                toolCalls.incrementAndGet();
                return arguments.get("value");
            }
        };
        var tools = new io.aigoodle.tool.ToolRegistry(List.of(tool), List.of());
        var caller = CurrentUser.builder()
                .tenantId("approval-tenant")
                .userId("approval-owner")
                .build();
        var first = new AgentScopeRuntime(
                java.util.Map.of(
                        "default",
                        new io.aigoodle.runtime.agentscope.DefaultAgentScopeAgentFactory(
                                models,
                                tools,
                                io.aigoodle.skill.service.SkillResolver.none(),
                                new io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore(jdbc))),
                store);
        var paused = UserContextHolder.callAs(
                caller,
                () -> first.run(
                        AgentDefinition.builder()
                                .id("approval-agent")
                                .tenantId("approval-tenant")
                                .approvalRequiredTools(java.util.Set.of("echo"))
                                .maxModelCalls(approvalRounds + 1)
                                .maxToolCalls(approvalRounds)
                                .build(),
                        AgentRequest.of("use echo")));
        assertEquals(
                io.aigoodle.agent.api.AgentResponse.Status.AWAITING_APPROVAL, paused.getStatus(), paused.getError());
        assertEquals(0, toolCalls.get());
        io.aigoodle.agent.api.AgentResponse completed = paused;
        for (int round = 1; round <= approvalRounds; round++) {
            var restored = new AgentScopeRuntime(
                    java.util.Map.of(
                            "default",
                            new io.aigoodle.runtime.agentscope.DefaultAgentScopeAgentFactory(
                                    models,
                                    tools,
                                    io.aigoodle.skill.service.SkillResolver.none(),
                                    new io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore(jdbc))),
                    store);
            String approvalId = "persisted-call-" + round;
            completed = UserContextHolder.callAs(
                    caller,
                    () -> restored.resume(
                            paused.getRunId(),
                            new io.aigoodle.agent.runtime.AgentResumeCommand(
                                    approvalId, io.aigoodle.agent.runtime.AgentResumeCommand.Decision.APPROVE)));
            if (round < approvalRounds) {
                assertEquals(
                        io.aigoodle.agent.api.AgentResponse.Status.AWAITING_APPROVAL,
                        completed.getStatus(),
                        completed.getError());
                assertEquals(
                        "persisted-call-" + (round + 1),
                        completed.getPendingApproval().getApprovalId());
                assertEquals(round, toolCalls.get());
            }
        }
        assertEquals("restored answer", completed.getText(), completed.getError());
        assertEquals(paused.getRunId(), completed.getRunId());
        assertEquals(approvalRounds, toolCalls.get());
        assertEquals(approvalRounds + 1, modelCalls.get());
        assertEquals(
                AgentRunStatus.COMPLETED,
                store.find("approval-tenant", paused.getRunId()).orElseThrow().status());
    }

    @Test
    void independentJdbcWritersCannotBothCommitTheSameVersion() throws Exception {
        var firstStore = new io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore(jdbc);
        var secondStore = new io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore(jdbc);
        assertEquals(1, firstStore.saveIfVersion("race-user", "race-session", "state", new TestState("initial"), 0));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return firstStore.saveIfVersion("race-user", "race-session", "state", new TestState("first"), 1);
            });
            var second = executor.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return secondStore.saveIfVersion("race-user", "race-session", "state", new TestState("second"), 1);
            });
            var versions = List.of(
                    first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, versions.stream().filter(version -> version == 2).count());
            assertEquals(
                    1,
                    versions.stream()
                            .filter(version -> version == io.agentscope.core.state.AgentStateStore.UNVERSIONED)
                            .count());
            assertEquals(
                    2,
                    firstStore
                            .getVersioned("race-user", "race-session", "state", TestState.class)
                            .version());
        }
    }

    @Test
    void sdkStateSurvivesStoreRecreationAndRejectsStaleVersion() {
        assertInstanceOf(io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore.class, sdkState);
        assertEquals(1, sdkState.saveIfVersion("user-a", "session-a", "test", new TestState("first"), 0));
        var reloaded = new io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore(jdbc);
        assertEquals(
                new TestState("first"),
                reloaded.getVersioned("user-a", "session-a", "test", TestState.class)
                        .value());
        assertEquals(
                io.agentscope.core.state.AgentStateStore.UNVERSIONED,
                reloaded.saveIfVersion("user-a", "session-a", "test", new TestState("stale"), 0));
        assertEquals(2, reloaded.saveIfVersion("user-a", "session-a", "test", new TestState("second"), 1));
        assertTrue(reloaded.get("user-b", "session-a", "test", TestState.class).isEmpty());
        reloaded.save("user-a", "session-a", "list", List.of(new TestState("list item")));
        assertEquals(
                List.of(new TestState("list item")), sdkState.getList("user-a", "session-a", "list", TestState.class));
        reloaded.delete("user-a", "session-a", "test");
        assertTrue(sdkState.get("user-a", "session-a", "test", TestState.class).isEmpty());
        reloaded.delete("user-a", "session-a");
        assertFalse(sdkState.exists("user-a", "session-a"));
    }

    @Autowired
    private CancellationSignals cancellation;

    static class CancellationSignals {
        final java.util.concurrent.CountDownLatch subscribed = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<String> runId =
                new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    }

    @Test
    void cancellationPersistsJdbcTerminalStateAndStopsTheSdkStream() throws Exception {
        var user = CurrentUser.builder()
                .tenantId("cancel-tenant")
                .userId("cancel-owner")
                .build();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> UserContextHolder.callAs(
                    user,
                    () -> runtime.run(
                            AgentDefinition.builder()
                                    .id("slow-agent")
                                    .tenantId("cancel-tenant")
                                    .runtimeRef("slowFactory")
                                    .build(),
                            AgentRequest.of("wait"))));
            assertTrue(cancellation.subscribed.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var snapshot = UserContextHolder.callAs(user, () -> runtime.cancel(cancellation.runId.get()));
            assertEquals(AgentRunStatus.CANCELLED, snapshot.status());
            assertEquals(
                    "Cancelled by caller",
                    running.get(5, java.util.concurrent.TimeUnit.SECONDS).getError());
            assertTrue(cancellation.cancelled.get());
            var saved = store.find("cancel-tenant", cancellation.runId.get()).orElseThrow();
            assertEquals(AgentRunStatus.CANCELLED, saved.status());
            assertNotNull(saved.finishedAt());
            assertTrue(store.events("cancel-tenant", saved.runId(), 0, 100).stream()
                    .anyMatch(event -> event.type().contains("CANCELLED")));
        }
    }

    @Test
    void sdkExecutionWritesJdbcLifecycleAndStepsWithoutLeakingToAnotherTenant() {
        assertInstanceOf(JdbcAgentRunStore.class, store);
        var user =
                CurrentUser.builder().tenantId("sdk-tenant").userId("sdk-owner").build();
        var response = UserContextHolder.callAs(
                user,
                () -> runtime.run(
                        AgentDefinition.builder()
                                .id("offline-agent")
                                .tenantId("sdk-tenant")
                                .runtimeType("AGENTSCOPE")
                                .runtimeRef("offlineFactory")
                                .build(),
                        AgentRequest.of("question")));
        assertEquals("persisted answer", response.getText(), response.getError());
        var saved = store.find("sdk-tenant", response.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.COMPLETED, saved.status());
        assertNotNull(saved.finishedAt());
        assertTrue(saved.responseJson().contains("persisted answer"));
        var continued = UserContextHolder.callAs(
                user,
                () -> runtime.run(
                        AgentDefinition.builder()
                                .id("offline-agent")
                                .tenantId("sdk-tenant")
                                .runtimeRef("offlineFactory")
                                .build(),
                        AgentRequest.builder()
                                .query("next question")
                                .conversationId(response.getConversationId())
                                .build()));
        assertEquals("persisted answer", continued.getText(), continued.getError());
        assertTrue(store.events("sdk-tenant", response.getRunId(), 0, 100).stream()
                .anyMatch(event ->
                        event.type().equals("AGENT_STEP") && event.payloadJson().contains("persisted answer")));
        UserContextHolder.runAs(CurrentUser.builder().tenantId("foreign").build(), () -> {
            assertTrue(runtime.findRun(response.getRunId()).isEmpty());
            assertTrue(runtime.runEvents(response.getRunId(), 0, 100).isEmpty());
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineFactory {
        @Bean
        CancellationSignals cancellationSignals() {
            return new CancellationSignals();
        }

        @Bean
        AgentScopeAgentFactory slowFactory(CancellationSignals signals) {
            return (definition, request, context) -> {
                signals.runId.set(context.get("runId"));
                return ReActAgent.builder()
                        .name("slow")
                        .model(new Model() {
                            public String getModelName() {
                                return "slow";
                            }

                            public Flux<ChatResponse> stream(
                                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                                return Flux.<ChatResponse>never()
                                        .doOnSubscribe(subscription -> signals.subscribed.countDown())
                                        .doOnCancel(() -> signals.cancelled.set(true));
                            }
                        })
                        .build();
            };
        }

        @Bean
        AgentScopeAgentFactory offlineFactory(io.agentscope.core.state.AgentStateStore stateStore) {
            return (definition, request, context) -> ReActAgent.builder()
                    .name("offline")
                    .stateStore(stateStore)
                    .model(new Model() {
                        public String getModelName() {
                            return "offline";
                        }

                        public Flux<ChatResponse> stream(
                                List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                            return Flux.just(ChatResponse.builder()
                                    .content(List.of(TextBlock.builder()
                                            .text("persisted answer")
                                            .build()))
                                    .finishReason("stop")
                                    .build());
                        }
                    })
                    .build();
        }
    }
}
