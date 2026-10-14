package io.aigoodle.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Body for {@code POST /workflows} / {@code PUT /workflows/{id}} /
 * {@code PUT /apps/{appId}/workflow/draft}. {@code mode} is a free-form tag:
 * use {@code "workflow"} for one-shot flows and {@code "chatflow"} for
 * chat-style ones.
 *
 * <p>{@code graph} is an <em>opaque</em> JSON node deliberately. The visual
 * designer emits a heavy VueFlow shape (per-node {@code dimensions} /
 * {@code computedPosition} / {@code handleBounds} / {@code selected} /
 * {@code events}, per-edge embedded {@code sourceNode}/{@code targetNode}
 * duplicates, and designer-only node types like {@code CONDITION} that don't
 * map 1:1 to the engine's {@link io.aigoodle.workflow.graph.NodeType}
 * enum). Binding this into a strongly-typed {@code WorkflowGraph} at the REST
 * layer throws on unknown enum values and forces the frontend to know about
 * every backend-side node type before it can save.</p>
 *
 * <p>Persistence stores the JSON as-is; the run path parses it into a typed
 * {@code WorkflowGraph} where {@link io.aigoodle.workflow.graph.NodeType}'s
 * {@code @JsonCreator} maps designer aliases (CONDITION → IF_ELSE, LOOP →
 * ITERATION …) to real engine node types.</p>
 */
@Data
public class WorkflowSaveRequest {

    /**
     * Owning app id (FK to {@code apps.id}) — <b>required</b>. The workflow
     * row's primary key is pinned to this value so every subsequent save for
     * the same app upserts the same row; there's always exactly one
     * {@code version='draft'} record per app. Only publish creates a fresh
     * snapshot row.
     * <p>A blank/missing {@code appId} means the caller is broken — the
     * controller / service will return {@code app_id_required} rather than
     * silently mint an orphaned row.</p>
     */
    private String appId;

    private String name;
    private String mode;
    private JsonNode graph;
}
