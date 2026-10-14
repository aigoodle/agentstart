package io.aigoodle.completion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.api.workflow.WorkflowRunRequest;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.StepRecord;
import io.aigoodle.workflow.service.WorkflowService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

/**
 * Verifies the reactive workflow-run stream emits the Dify-style event
 * sequence {@code workflow_started → node_finished (per node) → workflow_finished},
 * flushing each node result as soon as the engine reports it.
 */
class WorkflowStreamControllerTest {

    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final WorkflowStreamController controller = new WorkflowStreamController(workflowService);

    @Test
    void runGraphStreamEmitsStartedPerNodeAndFinished() throws Exception {
        JsonNode graph = new ObjectMapper().readTree("{\"nodes\":[]}");
        StepRecord step = node("node-1", NodeType.LLM, "总结", Map.of("text", "ok"), 42L);

        WorkflowRunResult runResult = new WorkflowRunResult();
        runResult.setRunId("run-1");
        runResult.succeed(Map.of("text", "ok"));
        runResult.setSteps(List.of(step));

        when(workflowService.runGraphForTenant(eq(graph), anyMap(), isNull(), eq("default"), eq("app-preview"), any()))
                .thenAnswer(invocation -> {
                    Consumer<StepRecord> listener = invocation.getArgument(5);
                    listener.accept(step);
                    return runResult;
                });

        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setGraph(graph);
        request.setAppId("app-preview");
        request.setData(Map.of("query", "hello"));

        List<ServerSentEvent<Object>> events =
                controller.runGraphStream(request).collectList().block(Duration.ofSeconds(5));

        assertThat(events).hasSize(3);
        assertThat(events.get(0).event()).isEqualTo("workflow_started");
        assertThat(events.get(1).event()).isEqualTo("node_finished");
        assertThat(events.get(2).event()).isEqualTo("workflow_finished");

        @SuppressWarnings("unchecked")
        Map<String, Object> started = (Map<String, Object>) events.get(0).data();
        assertThat(started).containsKey("run_id");
        assertThat(started).containsEntry("conversation_id", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) events.get(1).data();
        assertThat(node)
                .containsEntry("node_id", "node-1")
                .containsEntry("node_type", "LLM")
                .containsEntry("title", "总结")
                .containsEntry("elapsed_ms", 42L)
                .containsEntry("failed", false)
                .containsEntry("outputs", Map.of("text", "ok"));
        assertThat(node.get("run_id")).isEqualTo(started.get("run_id"));

        assertThat(events.get(2).data()).isSameAs(runResult);
    }

    @Test
    void runStreamUsesStoredWorkflowAndEmitsSameProtocol() {
        StepRecord step = node("node-2", NodeType.START, "开始", Map.of(), 1L);
        WorkflowRunResult runResult = new WorkflowRunResult();
        runResult.succeed(Map.of());

        when(workflowService.run(eq("wf-9"), anyMap(), isNull(), any())).thenAnswer(invocation -> {
            Consumer<StepRecord> listener = invocation.getArgument(3);
            listener.accept(step);
            return runResult;
        });

        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setData(Map.of());

        List<ServerSentEvent<Object>> events =
                controller.runStream("wf-9", request).collectList().block(Duration.ofSeconds(5));

        assertThat(events).hasSize(3);
        assertThat(events.get(0).event()).isEqualTo("workflow_started");
        assertThat(events.get(1).event()).isEqualTo("node_finished");
        assertThat(events.get(2).event()).isEqualTo("workflow_finished");
    }

    private static StepRecord node(
            String id, NodeType type, String title, Map<String, Object> outputs, long elapsedMillis) {
        StepRecord record = new StepRecord();
        record.setNodeId(id);
        record.setNodeType(type);
        record.setTitle(title);
        record.setOutputs(outputs);
        record.setElapsedMillis(elapsedMillis);
        record.setFailed(false);
        return record;
    }
}
