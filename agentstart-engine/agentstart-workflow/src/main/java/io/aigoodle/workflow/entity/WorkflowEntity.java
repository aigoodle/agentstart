package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A stored workflow definition (its graph as JSON). Aligned with Dify's shape:
 * every workflow row belongs to an app ({@link #appId}) and has a
 * {@link #version} string that is either {@code "draft"} (the mutable working
 * copy — exactly one per app) or a semver-ish snapshot label like {@code "1.0"}
 * / {@code "1.1"} etc. Publishing a draft copies its {@link #graphJson} into a
 * new immutable row with a numeric version; the draft row itself is never
 * replaced, so {@code apps.workflow_id} keeps pointing at the same id and
 * subsequent edits keep hitting the same durable id.
 *
 * <p>The four JSON side-channels ({@link #features}, {@link #environmentVariables},
 * {@link #conversationVariables}, {@link #output}) mirror Dify's schema so
 * downstream code (feature toggles, env-var scoping, structured output) can be
 * layered on later without another schema rewrite.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "goodle_workflows", autoResultMap = true)
public class WorkflowEntity extends BaseEntity {

    /**
     * FK to {@code apps.id}. Nullable for legacy standalone workflows created
     * before the app-scoping model landed — those keep working via the plain
     * {@code /workflows} endpoints.
     */
    private String appId;

    private String name;

    private String description;

    /**
     * Dify parity: {@code workflow} (one-shot) or {@code chatflow}
     * (conversational). Kept for backward compat with the legacy standalone
     * endpoints; new app-scoped workflows inherit {@code apps.mode}.
     */
    private String mode;

    /**
     * The workflow graph — nodes + edges + designer metadata ({@code viewport},
     * per-node UI fields). Persisted as JSON via MyBatis-Plus's
     * {@link JacksonTypeHandler}: the column type is {@code TEXT}/{@code JSON}
     * on the DB side, but the Java field is a {@link JsonNode} so the whole
     * REST layer (request DTO, entity, response) uses the same opaque tree —
     * no {@code String}/{@code JsonNode} conversions between layers.
     *
     * <p>The column name is {@code graph} (not {@code graph_json}) to line up
     * with the legacy {@code spring-agent-start} schema and Dify's naming.
     * MyBatis-Plus needs {@code autoResultMap=true} on the enclosing
     * {@code @TableName} for the type handler to kick in on SELECT.</p>
     */
    @TableField(value = "graph", typeHandler = JacksonTypeHandler.class)
    private JsonNode graph;

    /**
     * {@code "draft"} for the mutable working copy (exactly one per app), or a
     * label like {@code "1.0"} / {@code "1.1"} for immutable published
     * snapshots. Stored as string so semver-style labels round-trip cleanly.
     */
    private String version;

    /**
     * {@code true} once a snapshot has been created via publish; drafts stay
     * {@code false}. Keeping a separate flag (rather than only inferring from
     * version) makes "list draft vs published" queries straightforward.
     */
    private Boolean published;

    /**
     * JSON blob for app-level feature flags (opening statement, suggested
     * questions, file upload, TTS, etc). Dify parity — leave null until the
     * feature panel is wired in.
     */
    private String features;

    /** JSON array of {@code {name, value, description}} env vars scoped to this workflow. */
    private String environmentVariables;

    /** JSON array of session-scoped conversation variables (chatflow-only). */
    private String conversationVariables;

    /** JSON of output schema for the workflow — used by API consumers. */
    private String output;

    /**
     * User-visible label for this snapshot ("热修复 v1.1" etc). Dify parity.
     */
    private String markedName;

    /** Free-form note describing what changed in this snapshot. Dify parity. */
    private String markedComment;
}
