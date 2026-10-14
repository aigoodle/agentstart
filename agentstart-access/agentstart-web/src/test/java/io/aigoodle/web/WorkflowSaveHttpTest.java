package io.aigoodle.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.agent.service.AgentService;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Wire-level guard that {@code POST /agent-start/workflows} + {@code PUT
 * /agent-start/workflows/{id}} actually persist the {@code graph_json} column.
 * Reproduces the exact request the visual designer sends — heavy VueFlow
 * payload, {@code viewport}, edge {@code sourceNode}/{@code targetNode}
 * snapshots — and asserts the DB row comes back with the graph intact.
 *
 * <p>Written after a user report of "提交保存的工作流 json 数据没有在数据库存入":
 * the {@link WorkflowService}-level {@code WorkflowOpaqueSaveTest} covers the
 * service-layer round-trip; this test additionally proves Spring MVC's
 * {@code @RequestBody} binding to {@link io.aigoodle.web.dto.WorkflowSaveRequest}'s
 * {@code JsonNode graph} field works end-to-end so a regression at the
 * controller layer surfaces here.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WorkflowSaveHttpTest.TestApp.class)
@AutoConfigureWebTestClient
class WorkflowSaveHttpTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {}

    @Autowired
    private WebTestClient http;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void workflowSelectorReturnsOnlyPublishedWorkflowApplications() {
        String suffix = java.util.UUID.randomUUID().toString();
        String publishedId = createSelectorApp("published-" + suffix, "workflow", true);
        String draftId = createSelectorApp("draft-" + suffix, "workflow", false);
        String chatId = createSelectorApp("chat-" + suffix, "chat", true);

        JsonNode envelope = http.get()
                .uri("/agent-start/apps/selectors/workflows")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();

        assertThat(envelope).isNotNull();
        JsonNode options = envelope.path("data");
        assertThat(options.isArray()).isTrue();
        assertThat(options.findValuesAsText("appId")).contains(publishedId).doesNotContain(draftId, chatId);
        JsonNode selected = null;
        for (JsonNode option : options) {
            if (publishedId.equals(option.path("appId").asText())) selected = option;
        }
        assertThat(selected).isNotNull();
        assertThat(selected.path("workflowId").asText()).isEqualTo(publishedId);
    }

    private String createSelectorApp(String name, String mode, boolean published) {
        JsonNode envelope = http.post()
                .uri("/agent-start/apps")
                .bodyValue(Map.of("name", name, "mode", mode, "published", published))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(envelope).isNotNull();
        return envelope.path("data").path("id").asText();
    }

    @Test
    void postWorkflowsWithoutAppIdIsRejected() throws Exception {
        // appId is required — a caller that omits it is broken. The controller
        // must return app_id_required, not silently mint an orphaned row.
        Map<String, Object> body = Map.of(
                "tenantId", "wire-test",
                "name", "orphan",
                "mode", "workflow",
                "graph", MAPPER.readTree("{\"nodes\":[],\"edges\":[]}"));
        EnvelopeOfWorkflow env = http.post()
                .uri("/agent-start/workflows")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk() // ApiResponse envelope carries the error
                .expectBody(EnvelopeOfWorkflow.class)
                .returnResult()
                .getResponseBody();
        assertThat(env.getCode()).isEqualTo("app_id_required");
    }

    @Test
    void postWorkflowsPersistsGraphJsonToDatabase() throws Exception {
        String designerGraphJson =
                """
                {
                  "nodes": [
                    {"id":"1","type":"START",
                     "position":{"x":96,"y":96},
                     "dimensions":{"width":300,"height":84},
                     "handleBounds":{"source":[{"id":null,"type":"source"}]},
                     "data":{"label":"开始","mode":"WORKFLOW","variables":[]}},
                    {"id":"2","type":"LLM","position":{"x":496,"y":96},
                     "data":{"label":"LLM","prompt":""}},
                    {"id":"3","type":"END","position":{"x":896,"y":96},
                     "data":{"label":"结束","output":[]}}
                  ],
                  "edges": [
                    {"id":"e1-2","source":"1","target":"2","type":"custom",
                     "sourceNode":{"id":"1","type":"START"},
                     "targetNode":{"id":"2","type":"LLM"}},
                    {"id":"e2-3","source":"2","target":"3","type":"custom"}
                  ],
                  "viewport": {"x": 12, "y": 34, "zoom": 0.75}
                }
                """;
        JsonNode graphNode = MAPPER.readTree(designerGraphJson);
        String appId = createSelectorApp("app-post-" + java.util.UUID.randomUUID(), "workflow", false);
        Map<String, Object> body = Map.of(
                "appId", appId,
                "tenantId", "wire-test",
                "name", "draft-wire-1",
                "mode", "workflow",
                "graph", graphNode);

        WorkflowEntity saved = http.post()
                .uri("/agent-start/workflows")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfWorkflow.class)
                .returnResult()
                .getResponseBody()
                .getData();

        assertThat(saved)
                .as("controller response must include the saved entity")
                .isNotNull();
        assertThat(saved.getId())
                .as("Insert must pin the row's id to the appId")
                .isEqualTo(appId);
        assertThat(saved.getGraph())
                .as("graph on the response must reflect what was persisted")
                .isNotNull();
        assertThat(saved.getGraph().get("viewport")).isNotNull();

        // Re-read from the DB through the service to prove the row is actually
        // written — the controller could theoretically return a stale in-memory
        // entity if the mapper insert silently no-op'd.
        WorkflowEntity fetched = workflowService.require(saved.getId());
        JsonNode fetchedGraph = fetched.getGraph();
        assertThat(fetchedGraph)
                .as("workflows.graph column must be populated after POST")
                .isNotNull();
        assertThat(fetchedGraph.get("nodes").size()).isEqualTo(3);
        assertThat(fetchedGraph.get("viewport").get("zoom").asDouble()).isEqualTo(0.75);
    }

    @Test
    void postWorkflowsWithAppIdUpsertsSameRowNotDuplicates() throws Exception {
        // The core invariant the user reported: two saves for the same app
        // must land on the same PK, so the DB never grows a duplicate draft.
        // Only publish (a separate endpoint) creates a snapshot copy.
        String appId = createSelectorApp("app-upsert-" + java.util.UUID.randomUUID(), "workflow", false);
        String firstGraph =
                """
                {"nodes":[{"id":"1","type":"START","position":{"x":0,"y":0},"data":{}}],
                 "edges":[],"viewport":{"x":0,"y":0,"zoom":1.0}}
                """;
        String secondGraph =
                """
                {"nodes":[
                   {"id":"1","type":"START","position":{"x":0,"y":0},"data":{}},
                   {"id":"2","type":"LLM","position":{"x":300,"y":0},"data":{"label":"改了"}}
                 ],"edges":[{"id":"e","source":"1","target":"2"}],
                 "viewport":{"x":5,"y":6,"zoom":2.0}}
                """;

        WorkflowEntity a = postWorkflow(appId, "wf-upsert", MAPPER.readTree(firstGraph));
        WorkflowEntity b = postWorkflow(appId, "wf-upsert", MAPPER.readTree(secondGraph));

        assertThat(a.getId()).as("first save's id must equal appId").isEqualTo(appId);
        assertThat(b.getId()).as("second save must reuse the same PK").isEqualTo(a.getId());

        // Fetch fresh from DB — proves the second save was an UPDATE, not an INSERT.
        WorkflowEntity fetched = workflowService.require(appId);
        assertThat(fetched.getGraph().get("nodes").size())
                .as("row must reflect the second (updated) graph")
                .isEqualTo(2);
        assertThat(fetched.getGraph().get("viewport").get("zoom").asDouble()).isEqualTo(2.0);

        // Sanity: only one draft row for this app, not two.
        java.util.List<WorkflowEntity> allForApp = workflowService.listByApp(appId);
        long draftCount =
                allForApp.stream().filter(w -> "draft".equals(w.getVersion())).count();
        assertThat(draftCount)
                .as("upsert must not leave a stale duplicate draft behind")
                .isEqualTo(1);
    }

    private WorkflowEntity postWorkflow(String appId, String name, JsonNode graph) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("appId", appId);
        body.put("name", name);
        body.put("mode", "workflow");
        body.put("graph", graph);
        return http.post()
                .uri("/agent-start/workflows")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfWorkflow.class)
                .returnResult()
                .getResponseBody()
                .getData();
    }

    @Test
    void putAppWorkflowDraftPersistsGraphJsonEndToEnd() throws Exception {
        // Create a workflow-mode app via /agents; that also mints the paired
        // draft workflow row (id == app.id invariant) via AppController.
        Map<String, Object> agentBody = Map.of(
                "tenantId", "wire-test",
                "name", "flow-app",
                "mode", "workflow",
                "modelId", "",
                "strategy", "REACT",
                "toolNames", List.of(),
                "maxIterations", 6,
                "memoryEnabled", true,
                "memoryWindow", 20);
        String appId = http.post()
                .uri("/agent-start/agents")
                .bodyValue(agentBody)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfMap.class)
                .returnResult()
                .getResponseBody()
                .getData()
                .get("id")
                .toString();

        String draftGraph =
                """
                {
                  "nodes":[
                    {"id":"1","type":"START","position":{"x":10,"y":20},"data":{"label":"开始"}},
                    {"id":"2","type":"LLM","position":{"x":300,"y":20},"data":{"label":"LLM"}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"2"}],
                  "viewport":{"x":5,"y":6,"zoom":1.25}
                }
                """;
        Map<String, Object> saveBody = Map.of(
                "name", "flow-app",
                "mode", "workflow",
                "graph", MAPPER.readTree(draftGraph));

        http.put()
                .uri("/agent-start/apps/{id}/workflow/draft", appId)
                .bodyValue(saveBody)
                .exchange()
                .expectStatus()
                .isOk();

        // Read back via the same endpoint the drawer uses on next open.
        WorkflowEntity fetched = http.get()
                .uri("/agent-start/apps/{id}/workflow/draft", appId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfWorkflow.class)
                .returnResult()
                .getResponseBody()
                .getData();

        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(appId);
        JsonNode g = fetched.getGraph();
        assertThat(g)
                .as("PUT /apps/{id}/workflow/draft must persist the graph on the DB row")
                .isNotNull();
        assertThat(g.get("viewport").get("zoom").asDouble()).isEqualTo(1.25);
        assertThat(g.get("nodes").get(0).get("type").asText()).isEqualTo("START");
        assertThat(g.get("nodes").get(1).get("type").asText()).isEqualTo("LLM");
    }

    @Test
    void publishCreatesSnapshotAndRepointsApplication() {
        Map<String, Object> agentBody = Map.of(
                "tenantId",
                "wire-test",
                "name",
                "publish-flow",
                "mode",
                "workflow",
                "strategy",
                "REACT",
                "toolNames",
                List.of(),
                "maxIterations",
                6,
                "memoryEnabled",
                true,
                "memoryWindow",
                20);
        String appId = http.post()
                .uri("/agent-start/agents")
                .bodyValue(agentBody)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfMap.class)
                .returnResult()
                .getResponseBody()
                .getData()
                .get("id")
                .toString();

        WorkflowEntity snapshot = http.post()
                .uri("/agent-start/apps/{id}/workflow/publish", appId)
                .bodyValue(Map.of("markedName", "release-1", "markedComment", "first release"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfWorkflow.class)
                .returnResult()
                .getResponseBody()
                .getData();

        assertThat(snapshot.getId()).isNotEqualTo(appId);
        assertThat(snapshot.getVersion()).isEqualTo("release-1");
        assertThat(snapshot.getPublished()).isTrue();
        assertThat(workflowService.findDraft(appId)).isNotNull();
        assertThat(agentService.require(appId).getWorkflowId()).isEqualTo(snapshot.getId());
        assertThat(agentService.require(appId).getPublished()).isTrue();

        http.put()
                .uri("/agent-start/apps/{id}/workflow/draft", appId)
                .bodyValue(
                        Map.of("graph", Map.of("nodes", List.of(), "edges", List.of(), "viewport", Map.of("zoom", 2))))
                .exchange()
                .expectStatus()
                .isOk();

        WorkflowEntity restored = http.post()
                .uri("/agent-start/apps/{id}/workflow/restore/{snapshotId}", appId, snapshot.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EnvelopeOfWorkflow.class)
                .returnResult()
                .getResponseBody()
                .getData();
        assertThat(restored.getGraph()).isEqualTo(snapshot.getGraph());
    }

    /** Envelope shape for endpoints returning a raw map (e.g. AppEntity view). */
    static class EnvelopeOfMap {
        private String code;
        private String message;
        private Map<String, Object> data;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }
    }

    @Test
    void putWorkflowsUpdatesGraphJsonInDatabase() throws Exception {
        // Seed a row via the service (typed) so the update path is exercised in
        // isolation from insert.
        String appId = createSelectorApp("app-put-" + java.util.UUID.randomUUID(), "workflow", false);
        WorkflowEntity seed = workflowService.require(appId);

        String updatedGraphJson =
                """
                {
                  "nodes":[{"id":"1","type":"START","position":{"x":0,"y":0},"data":{}}],
                  "edges":[],
                  "viewport":{"x":100,"y":200,"zoom":1.5}
                }
                """;
        Map<String, Object> body = Map.of(
                "name", "seed-updated",
                "mode", "workflow",
                "graph", MAPPER.readTree(updatedGraphJson));

        http.put()
                .uri("/agent-start/workflows/{id}", seed.getId())
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk();

        WorkflowEntity fetched = workflowService.require(seed.getId());
        assertThat(fetched.getName()).isEqualTo("seed-updated");
        assertThat(fetched.getGraph())
                .as("graph must be overwritten with the PUT payload")
                .isNotNull();
        assertThat(fetched.getGraph().get("viewport").get("zoom").asDouble()).isEqualTo(1.5);
    }

    /** Envelope shape matching {@link io.aigoodle.api.ApiResponse}. */
    static class EnvelopeOfWorkflow {
        private String code;
        private String message;
        private WorkflowEntity data;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public WorkflowEntity getData() {
            return data;
        }

        public void setData(WorkflowEntity data) {
            this.data = data;
        }
    }
}
