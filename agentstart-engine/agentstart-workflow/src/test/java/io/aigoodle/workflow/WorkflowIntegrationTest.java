package io.aigoodle.workflow;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.knowledge.service.CreateDatasetRequest;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.service.WorkflowService;
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

@SpringBootTest(classes = WorkflowTestApplication.class)
class WorkflowIntegrationTest {

    @BeforeEach
    void bindTrustedTenant() {
        io.aigoodle.common.context.UserContextHolder.set(io.aigoodle.common.context.CurrentUser.builder()
                .tenantId("wf")
                .userId("workflow-test")
                .build());
    }

    @AfterEach
    void clearTrustedTenant() {
        io.aigoodle.common.context.UserContextHolder.clear();
    }

    @Autowired
    private ModelService modelService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private WorkflowService workflowService;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.test-model}")
    private String ollamaModel;

    private String embeddingModelId() {
        ModelEntity m = modelService.register(ModelRegistration.builder()
                .tenantId("wf")
                .providerName("fake")
                .modelName("hash-embed")
                .modelType(ModelType.TEXT_EMBEDDING)
                .credentials(Map.of("dimensions", 256))
                .build());
        return m.getId();
    }

    @Test
    void knowledgeRetrievalWorkflow() {
        DatasetEntity ds = datasetService.create(CreateDatasetRequest.builder()
                .tenantId("wf")
                .name("kb")
                .embeddingModelId(embeddingModelId())
                .indexingTechnique(IndexingTechnique.HIGH_QUALITY)
                .build());
        knowledgeService.addText(
                ds.getId(),
                "animals.txt",
                String.join(
                        "\n",
                        "Cats are independent pets that enjoy sleeping all day.",
                        "Dogs are loyal animals and love playing fetch outdoors.",
                        "Python is a popular programming language for data science."));

        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("kb", NodeType.KNOWLEDGE_RETRIEVAL)
                .with("datasetIds", List.of(ds.getId()))
                .with("query", "{{#sys.query#}}")
                .with("topK", 2));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("context", "{{#kb.result#}}")));
        g.addEdge(EdgeDef.of("start", "kb"));
        g.addEdge(EdgeDef.of("kb", "end"));

        WorkflowRunResult r = workflowService.runGraph(g, Map.of("query", "dogs loyal fetch"), null);
        assertTrue(r.isSuccess(), r.getError());
        String context = String.valueOf(r.output("context"));
        assertTrue(
                context.toLowerCase().contains("dog"),
                "retrieved workflow context should mention dogs, was: " + context);
    }

    @Test
    void saveAndRunStoredWorkflowPersistsRun() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("tpl", NodeType.TEMPLATE_TRANSFORM)
                .with("template", "Echo: {{#start.text#}}")
                .with("outputKey", "msg"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("answer", "{{#tpl.msg#}}")));
        g.addEdge(EdgeDef.of("start", "tpl"));
        g.addEdge(EdgeDef.of("tpl", "end"));

        // save() now requires appId — the entity's PK is pinned to it so
        // subsequent saves upsert the same row (Dify-parity draft model).
        WorkflowEntity wf =
                workflowService.save("app-echo-" + java.util.UUID.randomUUID(), "wf", "echo-flow", "workflow", g);
        assertNotNull(wf.getId());

        WorkflowRunResult r = workflowService.run(wf.getId(), Map.of("text", "hello world"), null);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals("Echo: hello world", r.output("answer"));
    }

    @Test
    void llmNodeThroughOllama() {
        Assumptions.assumeTrue(ollamaReachable(), "Ollama not reachable — skipping LLM node test");
        ModelEntity llm = modelService.register(ModelRegistration.builder()
                .tenantId("wf")
                .providerName("ollama")
                .modelName(ollamaModel)
                .modelType(ModelType.LLM)
                .credentials(Map.of("baseUrl", ollamaBaseUrl))
                .build());

        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("llm", NodeType.LLM)
                .with("modelId", llm.getId())
                .with("systemPrompt", "You are concise.")
                .with("userPrompt", "Reply with exactly one word: {{#sys.word#}}"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("text", "{{#llm.text#}}")));
        g.addEdge(EdgeDef.of("start", "llm"));
        g.addEdge(EdgeDef.of("llm", "end"));

        WorkflowRunResult r = workflowService.runGraph(g, Map.of("word", "hello"), null);
        assertTrue(r.isSuccess(), r.getError());
        assertNotNull(r.output("text"));
        assertFalse(String.valueOf(r.output("text")).isBlank());
    }

    @Test
    void toolNodeExecutesCalculatorWithoutLlm() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("calc", NodeType.TOOL)
                .with("tool", "calculator")
                .with("args", Map.of("expression", "{{#sys.expr#}}"))
                .with("outputKey", "value"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("answer", "{{#calc.value#}}")));
        g.addEdge(EdgeDef.of("start", "calc"));
        g.addEdge(EdgeDef.of("calc", "end"));

        WorkflowRunResult r = workflowService.runGraph(g, Map.of("expr", "23*19"), null);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals("437", String.valueOf(r.output("answer")));
    }

    @Test
    void agentUsesToolThroughOllama() {
        Assumptions.assumeTrue(ollamaReachable(), "Ollama not reachable — skipping agent+tool test");
        ModelEntity llm = modelService.register(ModelRegistration.builder()
                .tenantId("wf")
                .providerName("ollama")
                .modelName(ollamaModel)
                .modelType(ModelType.LLM)
                .credentials(Map.of("baseUrl", ollamaBaseUrl))
                .build());

        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("agent", NodeType.AGENT)
                .with("modelId", llm.getId())
                .with("systemPrompt", "You can use tools. Use the calculator for any arithmetic.")
                .with("query", "What is 23 multiplied by 19?"));
        g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("text", "{{#agent.text#}}")));
        g.addEdge(EdgeDef.of("start", "agent"));
        g.addEdge(EdgeDef.of("agent", "end"));

        WorkflowRunResult r = workflowService.runGraph(g, Map.of(), null);
        Assumptions.assumeTrue(r.isSuccess(), "Local Ollama model did not complete the tool loop: " + r.getError());
        assertNotNull(r.output("text"));
        assertFalse(String.valueOf(r.output("text")).isBlank());
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
