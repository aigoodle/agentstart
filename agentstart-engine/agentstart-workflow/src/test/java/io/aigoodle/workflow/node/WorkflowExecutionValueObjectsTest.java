package io.aigoodle.workflow.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowExecutionValueObjectsTest {

    @Test
    void startsExecutionContextWithInputsAvailableToNodesAndTemplates() {
        ExecutionContext context = ExecutionContext.start(Map.of("question", "Why?"), "conversation-1", null);

        assertEquals("conversation-1", context.getConversationId());
        assertEquals("Why?", context.getInputs().get("question"));
        assertEquals("Why?", context.getPool().get("sys.question"));
        assertFalse(context.getRunId().isBlank());
    }

    @Test
    void createsStepRecordFromCompletedNodeExecution() {
        NodeDef node = NodeDef.of("answer", NodeType.ANSWER);
        node.setTitle("Final answer");
        NodeResult nodeResult = NodeResult.of("text", "Done").handle("complete");

        StepRecord step = StepRecord.completed(node, nodeResult, 12);

        assertEquals("answer", step.getNodeId());
        assertEquals(NodeType.ANSWER, step.getNodeType());
        assertEquals("Done", step.getOutputs().get("text"));
        assertEquals("complete", step.getHandle());
        assertEquals(12, step.getElapsedMillis());
        assertFalse(step.isFailed());
    }

    @Test
    void transitionsWorkflowResultWithoutLeavingStaleErrorState() {
        ArrayList<StepRecord> steps = new ArrayList<>();
        WorkflowRunResult result = WorkflowRunResult.forRun("run-1", steps)
                .fail("temporary failure", Map.of())
                .succeed(Map.of("text", "Recovered"));

        assertEquals("run-1", result.getRunId());
        assertSame(steps, result.getSteps());
        assertTrue(result.isSuccess());
        assertNull(result.getError());
        assertEquals("Recovered", result.text());
    }
}
