package io.aigoodle.api.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Data;

/**
 * Body accepted by both {@code /workflows/{id}/run} and {@code /workflows/run-graph}.
 * When {@code graph} is set it's an ad-hoc run; when {@code workflowId} is set it's a
 * run of a stored workflow.
 *
 * <p>{@code graph} is kept opaque ({@link JsonNode}) for the same reason as
 * workflow save requests — the visual designer produces UI-heavy
 * JSON with alias node types. The service layer canonicalises via
 * the workflow engine's node type mapping
 * when materialising the typed graph for the engine.</p>
 */
@Data
public class WorkflowRunRequest {

    private String workflowId;
    /** Optional application attribution for designer preview; validated by the service. */
    private String appId;

    private JsonNode graph;
    /** Canonical START-node input object used by UI, API and durable triggers. */
    private Map<String, Object> data;
    /** @deprecated use {@link #data}; retained for wire compatibility. */
    @Deprecated
    private Map<String, Object> inputs;

    private String conversationId;
}
