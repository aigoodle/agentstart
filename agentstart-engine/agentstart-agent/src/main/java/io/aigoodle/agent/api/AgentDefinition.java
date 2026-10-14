package io.aigoodle.agent.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resolved runtime configuration, independent of its persistence representation. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition {

    private static final int DEFAULT_MAX_ITERATIONS = 6;
    private static final int DEFAULT_MEMORY_WINDOW = 20;

    private String id;
    /** Owning tenant used when resolving the configured provider and model. */
    private String tenantId;
    /** Trusted Java binding for shared workflow model configuration; not client input. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String resourceTenantId;

    private String name;

    /** NATIVE or an explicit host-provided runtime type. */
    @Builder.Default
    private String runtimeType = "NATIVE";

    /** Host-owned runtime resource/bean reference; interpreted only by the selected extension. */
    private String runtimeRef;

    /** System prompt / persona. May reference {@code {{variable}}} placeholders. */
    private String instructions;

    /** Plain vendor model name (e.g. {@code qwen3.6-plus}) the agent talks to. */
    private String modelName;

    /** Provider that owns {@link #modelName} (e.g. {@code qwen}). */
    private String modelProvider;

    @Builder.Default
    private AgentStrategyType strategy = AgentStrategyType.REACT;

    /** Names of tools (from the tool registry) this agent may use. Empty = all. */
    @Builder.Default
    private List<String> toolNames = List.of();

    /** Published business skills whose instructions are resolved at run start. */
    @Builder.Default
    private List<String> skillIds = List.of();

    /** Tools that require human approval before execution (HITL). */
    @Builder.Default
    private Set<String> approvalRequiredTools = new HashSet<>();

    /** Sub-agent ids this agent may delegate to (each exposed as a tool). */
    @Builder.Default
    private List<String> delegateAgentIds = List.of();

    @Builder.Default
    private int maxIterations = DEFAULT_MAX_ITERATIONS;

    /** Maximum model request boundaries per run; zero means no additional budget. */
    @Builder.Default
    private int maxModelCalls = 0;

    /** Maximum tool executions per run; zero means no additional budget. */
    @Builder.Default
    private int maxToolCalls = 0;

    @Builder.Default
    private boolean memoryEnabled = true;

    @Builder.Default
    private int memoryWindow = DEFAULT_MEMORY_WINDOW;

    /**
     * Per-application runtime overrides decoded from the model configuration.
     * Recognised keys:
     * {@code temperature, topP, maxTokens, presencePenalty, frequencyPenalty,
     * stop, thinkingMode}. Vendor-specific translation of {@code thinkingMode}
     * happens in {@code AgentChatOptionsFactory}, keyed by {@link #modelProvider}.
     */
    @Builder.Default
    private Map<String, Object> modelSettings = new HashMap<>();

    /** Returns the default reasoning strategy when a programmatic caller supplied null. */
    public AgentStrategyType getStrategy() {
        return strategy == null ? AgentStrategyType.REACT : strategy;
    }

    public List<String> getToolNames() {
        return toolNames == null ? List.of() : toolNames;
    }

    public Set<String> getApprovalRequiredTools() {
        return approvalRequiredTools == null ? Set.of() : approvalRequiredTools;
    }

    public List<String> getSkillIds() {
        return skillIds == null ? List.of() : skillIds;
    }

    public List<String> getDelegateAgentIds() {
        return delegateAgentIds == null ? List.of() : delegateAgentIds;
    }

    /** Guards strategy loops against invalid programmatic configuration. */
    public int getMaxIterations() {
        return maxIterations > 0 ? maxIterations : DEFAULT_MAX_ITERATIONS;
    }

    /** Guards memory implementations against invalid recall limits. */
    public int getMemoryWindow() {
        return memoryWindow > 0 ? memoryWindow : DEFAULT_MEMORY_WINDOW;
    }

    public Map<String, Object> getModelSettings() {
        return modelSettings == null ? Map.of() : modelSettings;
    }
}
