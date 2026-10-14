package io.aigoodle.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.mapper.AgentRunMapper;
import io.aigoodle.agent.runtime.AgentRunStatus;
import io.aigoodle.agent.service.AgentService;
import io.aigoodle.agent.service.SaveAppRequest;
import io.aigoodle.agent.support.ScriptedChatProvider;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AgentTestApplication.class)
class AgentRuntimeTest {

    @BeforeEach
    void enterTenantContext() {
        UserContextHolder.set(
                CurrentUser.builder().userId("agent-test").tenantId("ag").build());
    }

    @AfterEach
    void clearTenantContext() {
        UserContextHolder.clear();
    }

    @Autowired
    private ModelService modelService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    private AgentRunMapper agentRunMapper;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.test-model}")
    private String ollamaModel;

    /**
     * Register a scripted LLM so it exists in the catalog, then return its plain
     * vendor name — the agent runtime resolves (provider, name) → ModelEntity via
     * {@code ModelService.findOrMaterialize}, no UUID plumbing needed.
     */
    private String scriptedModel(String modelName) {
        modelService.register(ModelRegistration.builder()
                .tenantId("ag")
                .providerName("scripted")
                .modelName(modelName)
                .modelType(ModelType.LLM)
                .build());
        return modelName;
    }

    @Test
    void reactAgentUsesCalculatorTool() {
        ScriptedChatProvider.script(
                "react-calc",
                last -> last.startsWith("Observation:")
                        ? "Thought: done\nFinal Answer: "
                                + last.replace("Observation:", "").strip()
                        : "Thought: I should compute\nAction: calculator\nAction Input: {\"expression\":\"6*7\"}");

        AppEntity agent = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("calc")
                .instructions("You solve math using tools.")
                .modelProvider("scripted")
                .modelName(scriptedModel("react-calc"))
                .strategy(AgentStrategyType.REACT)
                .toolNames(List.of("calculator"))
                .memoryEnabled(false)
                .build());

        AgentResponse r = agentService.run(agent.getId(), AgentRequest.of("what is 6*7?"));
        assertTrue(r.isCompleted(), r.getError());
        assertNotNull(r.getRunId());
        assertEquals("42", r.getText());
        assertTrue(r.getSteps().stream().anyMatch(s -> "calculator".equals(s.getAction())));
        var persistedRun = agentService.findRun(r.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.COMPLETED, persistedRun.status());
        assertEquals(agent.getId(), persistedRun.agentId());
        assertNotNull(persistedRun.definitionJson());
        assertEquals(
                List.of(
                        "RUN_CREATED",
                        "RUN_RUNNING",
                        "STEP_ACTION",
                        "TOOL_SUCCEEDED",
                        "STEP_OBSERVATION",
                        "STEP_FINAL",
                        "RUN_COMPLETED"),
                agentService.runEvents(r.getRunId(), 0, 10).stream()
                        .map(event -> event.type())
                        .toList());

        UserContextHolder.set(CurrentUser.builder()
                .userId("foreign-user")
                .tenantId("tenant-b")
                .build());
        assertTrue(agentService.findRun(r.getRunId()).isEmpty());
        assertTrue(agentService.runEvents(r.getRunId(), 0, 10).isEmpty());
        Throwable unsafeLookup = assertThrows(RuntimeException.class, () -> agentRunMapper.selectById(r.getRunId()));
        while (unsafeLookup.getCause() != null) unsafeLookup = unsafeLookup.getCause();
        assertInstanceOf(SecurityException.class, unsafeLookup);
    }

    @Test
    void persistenceGuardRejectsCrossTenantAgentAndSidecarAccess() {
        AppEntity owned = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("guarded-agent")
                .mode("agent")
                .modelProvider("scripted")
                .modelName(scriptedModel("guarded-agent-model"))
                .build());

        assertTenantMismatch(() -> agentService.require("tenant-b", owned.getId()));
        assertTenantMismatch(() -> agentService.getModelConfig("tenant-b", owned.getId()));
        assertTenantMismatch(() -> agentService.create(SaveAppRequest.builder()
                .tenantId("tenant-b")
                .name("forged-agent")
                .mode("agent")
                .build()));
    }

    private static void assertTenantMismatch(org.junit.jupiter.api.function.Executable executable) {
        Throwable failure = assertThrows(RuntimeException.class, executable);
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        assertInstanceOf(SecurityException.class, root);
        assertTrue(root.getMessage().contains("authenticated tenant"));
    }

    @Test
    void streamingListenerReceivesStepsInRealTimeOrder() {
        // Deterministic ReAct: think → call tool → answer.
        ScriptedChatProvider.script(
                "react-stream",
                last -> last.startsWith("Observation:")
                        ? "Thought: got it\nFinal Answer: "
                                + last.replace("Observation:", "").strip()
                        : "Thought: I compute\nAction: calculator\nAction Input: {\"expression\":\"3+4\"}");
        AppEntity agent = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("stream-agent")
                .modelProvider("scripted")
                .modelName(scriptedModel("react-stream"))
                .strategy(AgentStrategyType.REACT)
                .toolNames(List.of("calculator"))
                .memoryEnabled(false)
                .build());

        java.util.List<io.aigoodle.agent.api.AgentStep> streamed = new java.util.ArrayList<>();
        AgentResponse r = agentService.run(agent.getId(), AgentRequest.of("what is 3+4?"), streamed::add);
        assertTrue(r.isCompleted(), r.getError());
        // Expected: ACTION (compute) → OBSERVATION (7) → FINAL (7)
        assertEquals(3, streamed.size(), "should stream 3 steps in real time");
        assertEquals(
                io.aigoodle.agent.api.AgentStep.Kind.ACTION, streamed.get(0).getKind());
        assertEquals(
                io.aigoodle.agent.api.AgentStep.Kind.OBSERVATION,
                streamed.get(1).getKind());
        assertEquals(io.aigoodle.agent.api.AgentStep.Kind.FINAL, streamed.get(2).getKind());
        // The streamed steps and the final response's steps must agree.
        assertEquals(r.getSteps(), streamed);
    }

    @Test
    void memoryPersistsAcrossRuns() {
        ScriptedChatProvider.script("mem-model", last -> "Thought: ok\nFinal Answer: ack");
        AppEntity agent = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("memo")
                .instructions("Chat.")
                .modelProvider("scripted")
                .modelName(scriptedModel("mem-model"))
                .strategy(AgentStrategyType.REACT)
                .toolNames(List.of())
                .memoryEnabled(true)
                .memoryWindow(20)
                .build());

        String conv = "conv-memory-1";
        agentService.run(
                agent.getId(),
                AgentRequest.builder().query("hello one").conversationId(conv).build());
        AgentResponse r2 = agentService.run(
                agent.getId(),
                AgentRequest.builder().query("hello two").conversationId(conv).build());
        assertEquals(conv, r2.getConversationId());

        assertEquals(
                4,
                memoryManager.history("ag", agent.getId(), conv, 20).size(),
                "two runs should persist user+assistant messages each in agentstart-memory");
    }

    @Test
    void planExecuteStrategyPlansThenExecutesThenSynthesizes() {
        ScriptedChatProvider.script("plan-model", last -> {
            if (last.contains("produce a plan as a JSON array")) {
                return "[\"use the calculator to compute 6*7\"]";
            }
            if (last.contains("Execute step:")) {
                return "Action: calculator\nAction Input: {\"expression\":\"6*7\"}";
            }
            if (last.contains("Produce the final answer")) {
                return "The result is 42.";
            }
            return "unexpected";
        });

        AppEntity agent = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("planner")
                .instructions("You plan and execute.")
                .modelProvider("scripted")
                .modelName(scriptedModel("plan-model"))
                .strategy(AgentStrategyType.PLAN_EXECUTE)
                .toolNames(List.of("calculator"))
                .memoryEnabled(false)
                .build());

        AgentResponse r = agentService.run(agent.getId(), AgentRequest.of("compute 6 times 7"));
        assertTrue(r.isCompleted(), r.getError());
        assertTrue(r.getText().contains("42"), "final answer should contain the computed value, was: " + r.getText());
        assertTrue(
                r.getSteps().stream().anyMatch(s -> "calculator".equals(s.getAction())),
                "plan-execute should have invoked the calculator tool");
        assertTrue(
                r.getSteps().stream().anyMatch(s -> "42".equals(s.getObservation())),
                "the tool observation should be 42");
    }

    @Test
    void multiAgentDelegation() {
        ScriptedChatProvider.script("worker-model", last -> "Thought: ok\nFinal Answer: worker-result");
        ScriptedChatProvider.script(
                "orchestrator-model",
                last -> last.startsWith("Observation:")
                        ? "Thought: done\nFinal Answer: "
                                + last.replace("Observation:", "").strip()
                        : "Thought: delegate\nAction: delegate_to_worker\nAction Input: {\"input\":\"do the task\"}");

        AppEntity worker = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("worker")
                .instructions("You are a specialist worker.")
                .modelProvider("scripted")
                .modelName(scriptedModel("worker-model"))
                .strategy(AgentStrategyType.REACT)
                .toolNames(List.of())
                .memoryEnabled(false)
                .build());

        AppEntity orchestrator = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("orch")
                .instructions("You delegate to specialists.")
                .modelProvider("scripted")
                .modelName(scriptedModel("orchestrator-model"))
                .strategy(AgentStrategyType.REACT)
                .toolNames(List.of())
                .delegateAgentIds(List.of(worker.getId()))
                .memoryEnabled(false)
                .build());

        AgentResponse r = agentService.run(orchestrator.getId(), AgentRequest.of("please complete the task"));
        assertTrue(r.isCompleted(), r.getError());
        assertEquals("worker-result", r.getText());
    }

    @Test
    void reactAgentThroughOllama() {
        Assumptions.assumeTrue(ollamaReachable(), "Ollama not reachable — skipping ReAct e2e");
        ModelEntity llm = modelService.register(ModelRegistration.builder()
                .tenantId("ag")
                .providerName("ollama")
                .modelName(ollamaModel)
                .modelType(ModelType.LLM)
                .credentials(Map.of("baseUrl", ollamaBaseUrl))
                .build());
        AppEntity agent = agentService.create(SaveAppRequest.builder()
                .tenantId("ag")
                .name("calc-live")
                .instructions("Solve math. Use the calculator tool for arithmetic.")
                .modelProvider(llm.getProviderName())
                .modelName(llm.getModelName())
                .strategy(AgentStrategyType.REACT)
                .toolNames(List.of("calculator"))
                .memoryEnabled(false)
                .maxIterations(5)
                .build());

        AgentResponse r = agentService.run(agent.getId(), AgentRequest.of("What is 12 multiplied by 12?"));
        assertNotNull(r.getText());
        assertFalse(r.getText().isBlank());
    }

    private boolean ollamaReachable() {
        try {
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build()
                    .send(
                            HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/tags"))
                                    .timeout(Duration.ofSeconds(2))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
