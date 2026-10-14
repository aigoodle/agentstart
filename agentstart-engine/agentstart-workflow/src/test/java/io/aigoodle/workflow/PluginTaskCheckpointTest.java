package io.aigoodle.workflow;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.connector.execution.*;
import io.aigoodle.workflow.engine.*;
import io.aigoodle.workflow.graph.*;
import io.aigoodle.workflow.node.builtin.*;
import io.aigoodle.workflow.service.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = WorkflowTestApplication.class)
class PluginTaskCheckpointTest {
    @Autowired
    WorkflowCheckpointStore store;

    @Test
    void restartQueriesSavedTaskWithoutResubmission() {
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger queried = new AtomicInteger();
        ConnectorExecutionGateway gateway = request -> {
            assertEquals("plugin-task-test", request.context().tenantId());
            assertEquals(Map.of("prompt", "original"), request.arguments());
            if (request.context().attributes().containsKey("pluginTask")) {
                assertEquals("QUERY", request.context().attributes().get("pluginTaskOperation"));
                queried.incrementAndGet();
                return ConnectorResult.success(Map.of("text", "finished"));
            }
            submitted.incrementAndGet();
            return pending();
        };
        String runId = UUID.randomUUID().toString();
        WorkflowGraph graph = graph();
        var waiting = runner(gateway)
                .start(
                        "plugin-task-test",
                        "video",
                        "v1",
                        graph,
                        Map.of(),
                        null,
                        WorkflowRunOptions.defaults().withRunId(runId));
        assertEquals(WorkflowRunStatus.WAITING, waiting.getStatus());
        var saved = store.require("plugin-task-test", runId);
        assertTrue(saved.getVariablePoolJson().contains("external-task-1"));
        assertNull(saved.getLeaseOwner());
        graph.getNodes().clear();
        var completed = runner(gateway).wakeSleep(saved);
        assertTrue(completed.isSuccess(), completed.getError());
        assertEquals("finished", completed.output("answer"));
        assertEquals(1, submitted.get());
        assertEquals(1, queried.get());
    }

    @Test
    void cancellationAndExpiryForwardOriginalTaskIdentity() {
        for (boolean timeout : List.of(false, true)) {
            AtomicInteger cancelled = new AtomicInteger();
            ConnectorExecutionGateway gateway = request -> {
                if ("CANCEL".equals(request.context().attributes().get("pluginTaskOperation"))) {
                    assertEquals("plugin-task-test", request.context().tenantId());
                    assertTrue(request.context()
                            .attributes()
                            .get("pluginTask")
                            .toString()
                            .contains("external-task-1"));
                    cancelled.incrementAndGet();
                    return ConnectorResult.success(Map.of("text", "cancelled"));
                }
                return pending();
            };
            String runId = UUID.randomUUID().toString();
            var first = runner(gateway);
            first.start(
                    "plugin-task-test",
                    "video",
                    "v1",
                    graph(),
                    Map.of(),
                    null,
                    WorkflowRunOptions.defaults().withRunId(runId));
            var restarted = runner(gateway);
            if (timeout) assertTrue(restarted.timeoutWait(store.require("plugin-task-test", runId)));
            else assertTrue(restarted.cancel("plugin-task-test", runId, "stop"));
            assertEquals(1, cancelled.get());
            assertEquals(
                    timeout ? "TIMED_OUT" : "CANCELLED",
                    store.require("plugin-task-test", runId).getStatus());
        }
    }

    @Test
    void parallelVideoNodesResumeWithoutDuplicateSubmission() {
        var submitted = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        ConnectorExecutionGateway gateway = request -> {
            assertEquals("media.video", request.connector().connectorId());
            String node = request.context().nodeId();
            if (request.context().attributes().containsKey("pluginTask"))
                return ConnectorResult.success(Map.of("videoUrl", "https://media.example/" + node + ".mp4"));
            submitted.computeIfAbsent(node, key -> new AtomicInteger()).incrementAndGet();
            try {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            return pending();
        };
        var graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        for (String id : List.of("left", "right")) {
            graph.addNode(NodeDef.of(id, NodeType.VIDEO_GENERATION)
                    .with("model", Map.of("modelId", "video"))
                    .with("prompt", "Sunrise")
                    .with("parameters", Map.of()));
            graph.addEdge(EdgeDef.of("start", id));
            graph.addEdge(EdgeDef.of(id, "end"));
        }
        graph.addNode(NodeDef.of("end", NodeType.END)
                .with(
                        "outputs",
                        Map.of(
                                "left",
                                "{{#left.result.data.videoUrl#}}",
                                "right",
                                "{{#right.result.data.videoUrl#}}")));
        String runId = UUID.randomUUID().toString();
        var result = runner(gateway)
                .start(
                        "plugin-task-test",
                        "video",
                        "v1",
                        graph,
                        Map.of(),
                        null,
                        WorkflowRunOptions.defaults().withRunId(runId));
        assertEquals(WorkflowRunStatus.WAITING, result.getStatus());
        for (int i = 0; i < 3 && result.getStatus() == WorkflowRunStatus.WAITING; i++)
            result = runner(gateway).wakeSleep(store.require("plugin-task-test", runId));
        assertTrue(result.isSuccess(), result.getError());
        assertEquals("https://media.example/left.mp4", result.output("left"));
        assertEquals("https://media.example/right.mp4", result.output("right"));
        assertEquals(1, submitted.get("left").get());
        assertEquals(1, submitted.get("right").get());
    }

    private PersistentWorkflowRunner runner(ConnectorExecutionGateway gateway) {
        return new PersistentWorkflowRunner(
                new WorkflowEngine(new NodeExecutorRegistry(List.of(
                        new StartNodeExecutor(),
                        new ConnectorNodeExecutor(gateway),
                        new VideoGenerationNodeExecutor(gateway),
                        new EndNodeExecutor()))),
                store);
    }

    private static ConnectorResult pending() {
        return new ConnectorResult(
                true,
                null,
                List.of(),
                null,
                Map.of(
                        "status",
                        "PENDING",
                        "pluginTask",
                        Map.of(
                                "pluginVersion",
                                "1",
                                "task",
                                Map.of(
                                        "id",
                                        "external-task-1",
                                        "state",
                                        Map.of(),
                                        "nextPollAt",
                                        Instant.now().plusSeconds(10).toString()))));
    }

    private static WorkflowGraph graph() {
        var graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("video", NodeType.CONNECTOR)
                .with("provider", "plugin")
                .with("connectorId", "test.video")
                .with("actionId", "generate")
                .with("inputs", Map.of("prompt", "original")));
        graph.addNode(
                NodeDef.of("end", NodeType.END).with("outputs", Map.of("answer", "{{#video.result.data.text#}}")));
        graph.addEdge(EdgeDef.of("start", "video"));
        graph.addEdge(EdgeDef.of("video", "end"));
        return graph;
    }
}
