package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The 1:1 sidecar of an {@link AppEntity} carrying everything about
 * <em>how the app behaves</em> — model reference + runtime overrides, prompts,
 * agent config (strategy / tools / delegation / memory), retrieval config and
 * user-input form. Mirrors Dify's {@code app_model_configs} table so the
 * front-end drawer maps 1:1 onto persisted fields.
 * <p>
 * Why a sidecar (not more columns on {@code apps}):
 * <ul>
 *   <li>{@code apps} stays a lean "what is this app" catalog row (name, icon,
 *       mode, publish state) — that's all the agent-list card renders, no
 *       need for a JOIN.</li>
 *   <li>For workflow / chatflow mode apps the prompt / model / retrieval
 *       config already live inside the {@code workflows} row's graph; those
 *       apps carry an empty {@code app_model_configs} row.</li>
 *   <li>The whole blob can be reset (restore-from-history) or copied
 *       (duplicate app) without touching the app catalog row.</li>
 * </ul>
 * <p>
 * The primary key is intentionally the app id itself — the row is strictly
 * 1:1 with {@code apps}. That lets us load / upsert in one PK-hit call
 * without a secondary index.
 * <p>
 * JSON columns are stored as opaque {@code TEXT} — same convention the rest
 * of the agent module already uses (see {@link AppEntity}). Callers
 * serialise with {@code JsonUtils}. The console can safely persist future
 * fields by nesting them inside a JSON blob without a schema change.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_app_model_configs")
public class AppModelConfigEntity extends BaseEntity {

    /** FK to {@code apps.id}. Value is identical to {@link #getId()}. */
    private String appId;

    // ------------------------------------------------------------- Selected model

    /** Provider key (e.g. {@code qwen}, {@code openai}, {@code ollama}). */
    private String modelProvider;

    /**
     * Plain vendor model name (e.g. {@code qwen3.6-flash}). Together with
     * {@link #modelProvider} the runtime resolves an {@code agent_model} row.
     */
    private String modelName;

    /** Explicit execution backend; defaults to NATIVE for existing applications. */
    private String runtimeType;

    /** Opaque host-owned reference resolved by the selected runtime extension. */
    private String runtimeRef;

    /**
     * Full Dify-shaped model reference blob:
     * {@code {provider, modelId, modelName, mode, completionParams}}.
     * Stored verbatim so the drawer can round-trip unchanged. Redundant with
     * {@link #modelProvider}+{@link #modelName} but preserved for parity so
     * the front end doesn't need a translation layer.
     */
    private String modelJson;

    /**
     * The vendor-neutral runtime overrides — the "模型设置" drawer payload:
     * {@code {temperature, topP, maxTokens, presencePenalty, frequencyPenalty,
     * stop, thinkingMode, extraBody, ...}}. Consumed at chat time by
     * {@code AgentChatOptionsFactory}, which translates {@code thinkingMode}
     * into vendor-specific params (Qwen's {@code enable_thinking}, Ollama's
     * {@code think}, Doubao's {@code thinking.type}, …).
     */
    private String configs;

    // -------------------------------------------------------------------- Prompt

    /** System prompt — the "编排" text area. Legacy {@code pre_prompt}. */
    private String prePrompt;

    /** {@code simple} / {@code advanced-chat} / {@code completion}. */
    private String promptType;

    /** Advanced-chat mode prompt config (Dify parity). */
    private String chatPromptConfig;

    /** Completion mode prompt config (Dify parity). */
    private String completionPromptConfig;

    // --------------------------------------------------------- Chat presentation

    /** First message the assistant posts unprompted when a new chat starts. */
    private String openingStatement;

    /** JSON array of one-click starter prompts shown as chips. */
    private String suggestedQuestionsJson;

    /** Dify's follow-up suggestions surfaced <em>after</em> each answer. */
    private String suggestedQuestionsAfterAnswer;

    /** Dify's "next question" reformulator toggle. */
    private String moreLikeThis;

    /** JSON blob describing the dynamic input form shown before starting a run. */
    private String userInputFormJson;

    // --------------------------------------------------------- Agent behaviour

    /**
     * JSON blob mirroring Dify's {@code agent_mode}:
     * {@code {enabled, strategy, tools, prompt}}. Consumed by the console
     * drawer's "编排" tab as the single source of truth for agent-mode
     * configuration; {@link #strategy} / {@link #toolNamesJson} are
     * denormalised copies so the runtime can read them without JSON parse.
     */
    private String agentMode;

    /** {@code REACT} / {@code FUNCTION_CALLING} / {@code PLAN_EXECUTE}. */
    private String strategy;

    /** JSON array of tool names this agent may use (empty/null = all). */
    private String toolNamesJson;

    /** JSON array of published declarative Skill ids enabled for this app. */
    private String skillIdsJson;

    /** JSON array of tool names requiring human approval. */
    private String approvalToolsJson;

    /** JSON array of sub-agent ids this agent may delegate to. */
    private String delegateAgentIdsJson;

    private Integer maxIterations;

    private Integer maxModelCalls;

    private Integer maxToolCalls;

    private Boolean memoryEnabled;

    private Integer memoryWindow;

    // ---------------------------------------------------------- Knowledge / RAG

    /** JSON array of knowledge-base ids this agent may retrieve from. */
    private String datasetIdsJson;

    /**
     * JSON blob describing retrieval overrides — top-k, reranker, weighted
     * vs. reranking-model mode. Legacy {@code dataset_configs}. Null = use
     * dataset defaults.
     */
    private String datasetConfigsJson;

    /** JSON blob toggling file uploads (image / document / audio) + provider config. */
    private String fileUploadJson;

    /** External data-source tool wiring (Dify parity, opaque JSON). */
    private String externalDataTools;

    /** Retrieval-source citation toggle (Dify parity). */
    private String retrieverResource;

    /** Variable name that carries the dataset query in advanced-chat mode. */
    private String datasetQueryVariable;

    // ---------------------------------------------------- Speech / moderation

    /** Speech-to-text config blob (Dify parity). */
    private String speechToText;

    /** Text-to-speech config blob (Dify parity). */
    private String textToSpeech;

    /** Sensitive-word moderation config blob (Dify parity). */
    private String sensitiveWordAvoidance;
}
