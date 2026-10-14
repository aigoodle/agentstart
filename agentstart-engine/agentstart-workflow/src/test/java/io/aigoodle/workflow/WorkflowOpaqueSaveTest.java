package io.aigoodle.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Regression test that guards the exact save path the frontend hits: POST
 * a heavy VueFlow-shaped JSON graph, then read it back and verify the
 * {@code graph_json} column actually contains it verbatim.
 *
 * <p>Written after a user report that "提交保存的工作流 json 数据没有在数据库
 * 存入" — i.e. the row was inserted but {@code graph_json} came back empty.
 * The fix here proves the {@link WorkflowService#save(String, String, String,
 * JsonNode)} overload persists the raw JSON (viewport included) and that
 * {@link WorkflowService#require(String)} round-trips it.</p>
 */
@SpringBootTest(classes = WorkflowTestApplication.class)
class WorkflowOpaqueSaveTest {

    @Autowired
    private WorkflowService workflowService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void savesHeavyDesignerGraphAndReadsItBack() throws Exception {
        String designerJson =
                """
                {
                  "nodes": [
                    {"id":"1","type":"START","position":{"x":96,"y":96},
                     "dimensions":{"width":300,"height":84},
                     "data":{"label":"开始","variables":[]}},
                    {"id":"2","type":"LLM","position":{"x":496,"y":96},
                     "data":{"label":"LLM","prompt":""}},
                    {"id":"3","type":"END","position":{"x":896,"y":96},
                     "data":{"label":"结束"}}
                  ],
                  "edges": [
                    {"id":"e1-2","type":"custom","source":"1","target":"2"},
                    {"id":"e2-3","type":"custom","source":"2","target":"3"}
                  ],
                  "viewport": {"x": 12, "y": 34, "zoom": 0.75}
                }
                """;
        JsonNode graph = MAPPER.readTree(designerJson);

        String appId = "app-opaque-" + java.util.UUID.randomUUID();
        WorkflowEntity saved = workflowService.save(appId, "wf-opaque", "draft-test", "workflow", graph);

        assertNotNull(saved.getId(), "Insert must have produced a UUID id");
        assertNotNull(saved.getGraph(), "graph must be populated after save");
        assertNotNull(saved.getGraph().get("viewport"), "viewport must round-trip through the save path");

        // Read back from the mapper — proves the row is actually in the DB with
        // the graph column populated, not just carried on the returned object.
        WorkflowEntity fetched = workflowService.require("wf-opaque", saved.getId());
        JsonNode fetchedGraph = fetched.getGraph();
        assertNotNull(fetchedGraph, "graph column must not be empty after DB read");
        assertEquals(3, fetchedGraph.get("nodes").size());
        assertEquals("START", fetchedGraph.get("nodes").get(0).get("type").asText());
        assertEquals("LLM", fetchedGraph.get("nodes").get(1).get("type").asText());
        assertEquals(
                0.75,
                fetchedGraph.get("viewport").get("zoom").asDouble(),
                1e-9,
                "viewport zoom must survive DB round-trip");
        assertEquals(12, fetchedGraph.get("viewport").get("x").asInt());
    }

    @Test
    void persistenceGuardRejectsCrossTenantWorkflowAccess() {
        UserContextHolder.set(
                CurrentUser.builder().userId("workflow-test").tenantId("wf-a").build());
        try {
            String appId = "guarded-workflow-" + java.util.UUID.randomUUID();
            WorkflowEntity saved = workflowService.save(appId, "wf-a", "guarded", "workflow", (JsonNode) null);
            assertTenantMismatch(() -> workflowService.require("wf-b", saved.getId()));
            assertTenantMismatch(
                    () -> workflowService.save(appId + "-forged", "wf-b", "forged", "workflow", (JsonNode) null));
        } finally {
            UserContextHolder.clear();
        }
    }

    private static void assertTenantMismatch(org.junit.jupiter.api.function.Executable executable) {
        Throwable failure = assertThrows(RuntimeException.class, executable);
        org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, failure);
    }
}
