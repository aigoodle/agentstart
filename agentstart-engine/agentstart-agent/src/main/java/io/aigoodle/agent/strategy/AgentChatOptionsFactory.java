package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.model.options.ChatOptionsFactory;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Agent-side adapter over {@link ChatOptionsFactory}. Kept as a separate class
 * (rather than a direct call site) so the agent runtime can grow strategy-only
 * defaults later (e.g. force {@code enable_thinking=true} for a specific
 * agent kind) without touching the shared, workflow-visible factory.
 * <p>
 * Historic API — {@link #build(AgentDefinition)} and
 * {@link #resolveThinkingMode(AgentDefinition)} — is preserved verbatim so
 * strategies and tests carry on unchanged.
 */
public final class AgentChatOptionsFactory {

    private AgentChatOptionsFactory() {}

    /**
     * Translate an agent's {@code apps.model_settings_json} blob into the
     * vendor-appropriate {@link ChatOptions}. Returns {@code null} when the
     * agent has no per-app overrides so the caller can skip the
     * {@code .options(...)} attachment.
     */
    public static ChatOptions build(AgentDefinition def) {
        if (def == null) return null;
        return ChatOptionsFactory.buildFromSettings(def.getModelProvider(), def.getModelName(), def.getModelSettings());
    }

    /**
     * Resolve the app's normalized thinking mode ({@code "enabled"},
     * {@code "disabled"}, or {@code null} = vendor default). Used by
     * strategies that need to adapt the prompt or output filter (ReAct hides
     * the {@code Thought:} preamble when reasoning is disabled).
     */
    public static String resolveThinkingMode(AgentDefinition def) {
        if (def == null) return null;
        return ChatOptionsFactory.resolveThinkingMode(def.getModelSettings());
    }
}
