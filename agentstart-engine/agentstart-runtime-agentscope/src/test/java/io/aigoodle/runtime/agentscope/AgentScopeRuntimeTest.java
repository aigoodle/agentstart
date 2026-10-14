package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class AgentScopeRuntimeTest {
    @Test
    void cancellationStopsPublisherKeepsCancelledStateAndRejectsForeignTenant() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var cancelled = new AtomicBoolean();
        var runId = new java.util.concurrent.atomic.AtomicReference<String>();
        var runtime = new AgentScopeRuntime(Map.of("default", (definition, request, context) -> {
            runId.set(context.get("runId"));
            return ReActAgent.builder()
                    .name("waiting")
                    .model(model(Flux.<ChatResponse>never()
                            .doOnSubscribe(subscription -> started.countDown())
                            .doOnCancel(() -> cancelled.set(true))))
                    .build();
        }));
        var caller = CurrentUser.builder().tenantId("tenant-a").userId("owner").build();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> UserContextHolder.callAs(
                    caller,
                    () -> runtime.run(
                            AgentDefinition.builder().tenantId("tenant-a").build(), AgentRequest.of("wait"))));
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            UserContextHolder.runAs(
                    CurrentUser.builder().tenantId("tenant-b").build(),
                    () -> assertThrows(PlatformException.class, () -> runtime.cancel(runId.get())));
            var snapshot = UserContextHolder.callAs(caller, () -> runtime.cancel(runId.get()));
            assertEquals(io.aigoodle.agent.runtime.AgentRunStatus.CANCELLED, snapshot.status());
            assertEquals(
                    "Cancelled by caller",
                    future.get(5, java.util.concurrent.TimeUnit.SECONDS).getError());
            assertTrue(cancelled.get());
            UserContextHolder.runAs(caller, () -> {
                assertEquals(
                        io.aigoodle.agent.runtime.AgentRunStatus.CANCELLED,
                        runtime.findRun(runId.get()).orElseThrow().status());
                assertEquals(snapshot, runtime.cancel(runId.get()));
            });
        }
    }

    @Test
    void realSdkRunProducesTextStepsAndTenantScopedSessionKeys() {
        List<RuntimeContext> contexts = new ArrayList<>();
        AgentScopeRuntime runtime = new AgentScopeRuntime(Map.of("default", (definition, request, context) -> {
            contexts.add(context);
            return ReActAgent.builder()
                    .name("offline")
                    .sysPrompt("Respond briefly")
                    .model(model(Flux.just(ChatResponse.builder()
                            .content(List.of(
                                    TextBlock.builder().text("offline answer").build()))
                            .finishReason("stop")
                            .build())))
                    .build();
        }));
        List<String> tokens = new ArrayList<>();
        List<io.aigoodle.agent.api.AgentStep> steps = new ArrayList<>();
        for (String tenant : List.of("tenant-a", "tenant-b")) {
            UserContextHolder.runAs(
                    CurrentUser.builder().tenantId(tenant).userId("same-user").build(), () -> {
                        AgentDefinition definition = AgentDefinition.builder()
                                .id("agent")
                                .tenantId(tenant)
                                .build();
                        AgentRequest request = AgentRequest.builder()
                                .query("question")
                                .conversationId("same-session")
                                .build();
                        AgentResponse response = runtime.run(definition, request, steps::add, tokens::add);
                        assertEquals(AgentResponse.Status.COMPLETED, response.getStatus(), response.getError());
                        assertEquals("offline answer", response.getText());
                        assertEquals("same-session", response.getConversationId());
                        assertNotNull(response.getRunId());
                        assertEquals(response.getRunId(), contexts.getLast().get("runId"));
                        var saved = runtime.findRun(response.getRunId()).orElseThrow();
                        assertEquals(io.aigoodle.agent.runtime.AgentRunStatus.COMPLETED, saved.status());
                        assertEquals(tenant, saved.tenantId());
                        assertEquals(
                                "AGENTSCOPE",
                                io.aigoodle.common.util.JsonUtils.parse(saved.definitionJson(), AgentDefinition.class)
                                        .getRuntimeType());
                        assertTrue(runtime.runEvents(response.getRunId(), 0, 100).stream()
                                .anyMatch(event -> event.type().equals("AGENT_STEP")));
                        UserContextHolder.runAs(
                                CurrentUser.builder().tenantId("other-tenant").build(), () -> {
                                    assertTrue(
                                            runtime.findRun(response.getRunId()).isEmpty());
                                    assertTrue(runtime.runEvents(response.getRunId(), 0, 100)
                                            .isEmpty());
                                });
                    });
        }
        assertNotEquals(contexts.get(0).getUserId(), contexts.get(1).getUserId());
        assertNotEquals(contexts.get(0).getSessionId(), contexts.get(1).getSessionId());
        assertFalse(tokens.isEmpty());
        assertTrue(steps.stream().anyMatch(step -> step.getKind() == io.aigoodle.agent.api.AgentStep.Kind.FINAL));
    }

    @Test
    void timeoutCancelsModelPublisherAndReportsFailedExecution() {
        AtomicBoolean cancelled = new AtomicBoolean();
        var runtime = new AgentScopeRuntime(Map.of("default", (definition, request, context) -> ReActAgent.builder()
                .name("slow")
                .model(model(Flux.<ChatResponse>never().doOnCancel(() -> cancelled.set(true))))
                .build()));
        var response = runtime.run(
                AgentDefinition.builder().build(),
                AgentRequest.builder().query("hello").timeoutMillis(50L).build(),
                null,
                null);
        assertEquals(AgentResponse.Status.FAILED, response.getStatus());
        assertTrue(cancelled.get());
        assertEquals(
                io.aigoodle.agent.runtime.AgentRunStatus.TIMED_OUT,
                runtime.findRun(response.getRunId()).orElseThrow().status());
    }

    @Test
    void rejectsForeignTenantUnknownFactoriesAndUnconfiguredApproval() {
        var runtime = new AgentScopeRuntime(Map.of());
        assertThrows(
                PlatformException.class,
                () -> runtime.run(
                        AgentDefinition.builder().runtimeRef("unknown").build(), AgentRequest.of("hello"), null, null));
        UserContextHolder.runAs(
                CurrentUser.builder().tenantId("a").userId("user").build(), () -> {
                    assertThrows(
                            SecurityException.class,
                            () -> runtime.run(
                                    AgentDefinition.builder().tenantId("b").build(),
                                    AgentRequest.of("hello"),
                                    null,
                                    null));
                });
        assertThrows(
                PlatformException.class,
                () -> runtime.run(
                        AgentDefinition.builder()
                                .approvalRequiredTools(Set.of("sensitive-tool"))
                                .build(),
                        AgentRequest.of("hello"),
                        null,
                        null));
    }

    private static Model model(Flux<ChatResponse> output) {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return output;
            }

            @Override
            public String getModelName() {
                return "offline";
            }
        };
    }
}
