package io.aigoodle.workflow.graph;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in node types. Each maps to a {@code NodeExecutor}. Third parties can add
 * their own types/executors as Spring beans (n8n-style connector model).
 *
 * <p>Values from the visual designer arrive in a mix of casings and Dify-style
 * aliases (e.g. {@code CONDITION} instead of {@code IF_ELSE},
 * {@code CLASSIFIER} instead of {@code QUESTION_CLASSIFIER}). The
 * {@link #fromJson(String)} creator normalises those on deserialisation so a
 * designer graph round-trips through the engine without a translation layer
 * in the frontend.</p>
 */
public enum NodeType {
    START,
    END,
    ANSWER,
    TEMPLATE_TRANSFORM,
    VARIABLE_ASSIGNER,
    VARIABLE_AGGREGATOR,
    IF_ELSE,
    HTTP_REQUEST,
    SERVICE_API,
    SCHEDULE_TRIGGER,
    LLM,
    KNOWLEDGE_RETRIEVAL,
    QUESTION_CLASSIFIER,
    PARAMETER_EXTRACTOR,
    DOCUMENT_EXTRACTOR,
    LIST_OPERATOR,
    AGENT,
    ITERATION,
    CODE,
    CONNECTOR,
    VIDEO_GENERATION,
    TOOL,
    HUMAN_INPUT,
    APPROVAL,
    WAIT_EVENT,
    SLEEP_UNTIL;

    /**
     * Designer / Dify-parity aliases mapped onto the engine's canonical enum
     * names. Case is normalised to UPPER_SNAKE first, so both
     * {@code "condition"} and {@code "CONDITION"} land on {@link #IF_ELSE}.
     * Keep the values that are already canonical out of this map — the fallback
     * {@link #valueOf(String)} covers them.
     */
    private static final Map<String, NodeType> ALIASES = Map.ofEntries(
            Map.entry("CONDITION", IF_ELSE),
            Map.entry("IF", IF_ELSE),
            Map.entry("LOOP", ITERATION),
            Map.entry("HTTP", HTTP_REQUEST),
            Map.entry("TEMPLATE", TEMPLATE_TRANSFORM),
            Map.entry("CLASSIFIER", QUESTION_CLASSIFIER),
            Map.entry("VARIABLE", VARIABLE_ASSIGNER),
            Map.entry("VARIABLES", VARIABLE_ASSIGNER),
            Map.entry("AGGREGATOR", VARIABLE_AGGREGATOR),
            // The designer's chat-flow "user input" / "human input" seeds map
            // onto START — they're the graph's entry point in disguise.
            Map.entry("USER-INPUT", START),
            Map.entry("USER_INPUT", START),
            Map.entry("FILE-UPLOAD", START),
            Map.entry("FILE_UPLOAD", START));

    /**
     * Lenient JSON creator: uppercases the incoming value, tries the alias
     * table, then falls back to {@link #valueOf(String)}. Unknown values throw
     * {@link IllegalArgumentException} with a clear message — Jackson surfaces
     * that as a 400 rather than a cryptic enum error.
     */
    @JsonCreator
    public static NodeType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // Kebab-case ("user-input") preserved so the ALIASES table can hit it
        // verbatim; snake-case fallback normalises for valueOf().
        String upper = value.trim().toUpperCase(Locale.ROOT);
        NodeType aliased = ALIASES.get(upper);
        if (aliased != null) {
            return aliased;
        }
        String snake = upper.replace('-', '_');
        try {
            return NodeType.valueOf(snake);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown node type: " + value, ex);
        }
    }
}
