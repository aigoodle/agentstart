package io.aigoodle.workflow.node;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import java.time.Instant;
import java.util.Map;
import lombok.Data;

/**
 * One executed node, captured for observability / debugging / replay.
 */
@Data
public class StepRecord {

    private String nodeId;
    private NodeType nodeType;
    private String title;
    private Map<String, Object> outputs;
    private String handle;
    private long elapsedMillis;
    private boolean failed;
    private String error;
    private int attempt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long tokenCount;
    private String cost;
    private Integer externalStatus;
    private String traceId;
    private String spanId;

    public static StepRecord completed(NodeDef node, NodeResult result, long elapsedMillis) {
        return completed(node, result, elapsedMillis, 1, Instant.now().minusMillis(elapsedMillis), Instant.now());
    }

    public static StepRecord completed(
            NodeDef node, NodeResult result, long elapsedMillis, int attempt, Instant startedAt, Instant finishedAt) {
        StepRecord step = new StepRecord();
        step.nodeId = node.getId();
        step.nodeType = node.getType();
        step.title = node.getTitle();
        step.outputs = result.getOutputs();
        step.handle = result.getHandle();
        step.elapsedMillis = elapsedMillis;
        step.failed = result.isFailed();
        step.error = result.getError();
        step.attempt = attempt;
        step.startedAt = startedAt;
        step.finishedAt = finishedAt;
        step.tokenCount = result.getTokenCount();
        step.cost = result.getCost();
        step.externalStatus = result.getExternalStatus();
        step.traceId = org.slf4j.MDC.get("traceId");
        step.spanId = org.slf4j.MDC.get("spanId");
        return step;
    }
}
