package io.aigoodle.model.options;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Turns a normalized, Dify-shape {@code completionParams} / {@code
 * modelSettings} map into a Spring AI {@link ChatOptions} for the configured
 * vendor. Lives in {@code agent-start-model} so both the agent runtime
 * ({@code AgentChatOptionsFactory} in {@code agent-start-agent}) and the
 * workflow LLM node ({@code LlmNodeExecutor} in {@code agent-start-workflow})
 * can share one source of truth — otherwise a new vendor toggle (say Doubao
 * adding a "thinking_budget" param) would have to be duplicated in two places
 * and inevitably drift out of sync.
 * <p>
 * The blob is vendor-neutral (temperature / topP / maxTokens / penalties /
 * stop / a normalized {@code thinkingMode}); this factory does the vendor-side
 * translation:
 * <ul>
 *   <li>{@code ollama} — native {@link OllamaChatOptions} with
 *       {@code enableThinking() / disableThinking()} for reasoning-capable
 *       models (qwen3, deepseek-r1, gpt-oss, etc.)</li>
 *   <li>{@code openai} + all OpenAI-compat vendors — {@link OpenAiChatOptions}
 *       with common fields plus an {@code extraBody} entry per vendor:
 *       <ul>
 *         <li>Qwen / DashScope → {@code enable_thinking: true|false}</li>
 *         <li>Zhipu GLM-4.5+ → {@code thinking: {type: enabled|disabled}}</li>
 *         <li>Volcengine Doubao → {@code thinking: {type: enabled|disabled}}</li>
 *         <li>OpenAI o-series → {@code reasoning_effort: low}</li>
 *       </ul>
 *   </li>
 * </ul>
 * Native SDK providers (spring-ai-zhipuai, spring-ai-deepseek) whose
 * {@code ChatModel} rejects a foreign {@code ChatOptions} type silently drop
 * the extras and only keep the merged common fields — safe by default.
 * <p>
 * Returns {@code null} when the settings map is empty so the caller can skip
 * attaching options entirely.
 */
public final class ChatOptionsFactory {

    private static final Logger logger = LoggerFactory.getLogger(ChatOptionsFactory.class);

    private ChatOptionsFactory() {}

    public static ChatOptions buildFromSettings(String providerName, String modelName, Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        String provider = providerName == null ? "" : providerName.toLowerCase(Locale.ROOT);
        ChatSettingValues values = new ChatSettingValues(settings);
        ChatOptions options = "ollama".equals(provider)
                ? buildOllama(modelName, values)
                : buildOpenAiCompatible(provider, modelName, values);
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Built ChatOptions for provider={} model={} settings={} → {}",
                    provider,
                    modelName,
                    settings,
                    describe(options));
        }
        return options;
    }

    /**
     * Resolve the normalized thinking mode ({@code "enabled" | "disabled" |
     * null}) from a settings map. {@code null} = "no override — vendor default
     * applies". Used by strategies that need to adapt the prompt or output
     * filter independently of the ChatOptions build (ReAct hides the
     * {@code Thought:} preamble when reasoning is disabled).
     */
    public static String resolveThinkingMode(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        return new ChatSettingValues(settings).thinkingMode();
    }

    /** Compact one-liner for the debug log — full toString is noisy. */
    private static String describe(ChatOptions options) {
        StringBuilder description = new StringBuilder("{");
        description.append("model=").append(options.getModel());
        description.append(", temp=").append(options.getTemperature());
        description.append(", topP=").append(options.getTopP());
        description.append(", maxTokens=").append(options.getMaxTokens());
        if (options instanceof OpenAiChatOptions openAiOptions
                && openAiOptions.getExtraBody() != null
                && !openAiOptions.getExtraBody().isEmpty()) {
            description.append(", extraBody=").append(openAiOptions.getExtraBody());
        }
        description.append("}");
        return description.toString();
    }

    // ---------------------------------------------------------------- OpenAI

    private static ChatOptions buildOpenAiCompatible(String provider, String modelName, ChatSettingValues settings) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (modelName != null && !modelName.isBlank()) {
            builder.model(modelName);
        }
        applyCommonOpenAiOptions(builder, settings);
        Map<String, Object> extraBody = buildExtraBody(provider, settings, builder);
        if (extraBody != null && !extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        return builder.build();
    }

    private static void applyCommonOpenAiOptions(OpenAiChatOptions.Builder builder, ChatSettingValues settings) {
        Double temperature = settings.decimal("temperature");
        if (temperature != null) builder.temperature(temperature);
        Double topP = settings.decimal("topP", "top_p");
        if (topP != null) builder.topP(topP);
        Integer maxTokens = settings.integer("maxTokens", "max_tokens");
        if (maxTokens != null) builder.maxTokens(maxTokens);
        Double presencePenalty = settings.decimal("presencePenalty", "presence_penalty");
        if (presencePenalty != null) builder.presencePenalty(presencePenalty);
        Double frequencyPenalty = settings.decimal("frequencyPenalty", "frequency_penalty");
        if (frequencyPenalty != null) builder.frequencyPenalty(frequencyPenalty);
        List<String> stopSequences = settings.stringList("stop", "stop_sequences");
        if (stopSequences != null && !stopSequences.isEmpty()) builder.stop(stopSequences);
    }

    /**
     * Vendor-specific translation of {@code thinkingMode} into either a raw
     * body param (OpenAI-compat vendors) or, for OpenAI itself, the standard
     * {@code reasoning_effort} field. Returns {@code null} when the caller
     * hasn't set a thinking mode or the vendor has no known toggle.
     */
    private static Map<String, Object> buildExtraBody(
            String provider, ChatSettingValues settings, OpenAiChatOptions.Builder builder) {
        String thinkingMode = settings.thinkingMode();
        Map<String, Object> extras = new LinkedHashMap<>();
        Object configuredExtras = settings.first("extraBody", "extra_body");
        if (configuredExtras instanceof Map<?, ?> extraEntries) {
            for (Map.Entry<?, ?> entry : extraEntries.entrySet()) {
                extras.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        Integer thinkingBudget = settings.integer("thinkingBudget", "thinking_budget");
        if (thinkingBudget != null) {
            extras.put("thinking_budget", thinkingBudget);
        }
        Boolean webSearch = settings.bool("enableWebSearch", "enable_web_search");
        if (webSearch != null) {
            extras.put("enable_search", webSearch);
        }
        Integer seed = settings.integer("seed");
        if (seed != null) {
            extras.put("seed", seed);
        }
        Double repetitionPenalty = settings.decimal("repetitionPenalty", "repetition_penalty");
        if (repetitionPenalty != null) {
            extras.put("repetition_penalty", repetitionPenalty);
        }
        if (thinkingMode == null) {
            return extras;
        }
        switch (provider) {
            case "qwen", "dashscope" -> extras.put("enable_thinking", "enabled".equals(thinkingMode));
            case "zhipu", "bigmodel" -> extras.put("thinking", Map.of("type", thinkingMode));
            case "volcengine", "doubao", "ark" -> extras.put("thinking", Map.of("type", thinkingMode));
            case "openai" -> {
                if ("enabled".equals(thinkingMode)) {
                    builder.reasoningEffort("medium");
                } else if ("disabled".equals(thinkingMode)) {
                    builder.reasoningEffort("low");
                }
            }
            case "deepseek" -> {
                // DeepSeek toggles reasoning by model name (deepseek-reasoner
                // vs deepseek-chat) — no in-body switch. Nothing to add.
            }
            case "moonshot", "siliconflow" -> {
                // No documented thinking toggle at time of writing. Leave the
                // persisted mode alone so the console still round-trips.
            }
            default -> {
                // Unknown provider — do nothing, settings still round-trip so
                // a future provider bean can consume it.
            }
        }
        return extras;
    }

    // ---------------------------------------------------------------- Ollama

    private static ChatOptions buildOllama(String modelName, ChatSettingValues settings) {
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
        if (modelName != null && !modelName.isBlank()) {
            builder.model(modelName);
        }
        Double temperature = settings.decimal("temperature");
        if (temperature != null) builder.temperature(temperature);
        Double topP = settings.decimal("topP", "top_p");
        if (topP != null) builder.topP(topP);
        Integer topK = settings.integer("topK", "top_k");
        if (topK != null && topK > 0) builder.topK(topK);
        Integer maxTokens = settings.integer("maxTokens", "max_tokens");
        if (maxTokens != null) builder.numPredict(maxTokens);
        Double presencePenalty = settings.decimal("presencePenalty", "presence_penalty");
        if (presencePenalty != null) builder.presencePenalty(presencePenalty);
        Double frequencyPenalty = settings.decimal("frequencyPenalty", "frequency_penalty");
        if (frequencyPenalty != null) builder.frequencyPenalty(frequencyPenalty);
        List<String> stopSequences = settings.stringList("stop", "stop_sequences");
        if (stopSequences != null && !stopSequences.isEmpty()) builder.stop(stopSequences);

        String thinkingMode = settings.thinkingMode();
        if ("enabled".equals(thinkingMode)) {
            builder.enableThinking();
        } else if ("disabled".equals(thinkingMode)) {
            builder.disableThinking();
        }
        return builder.build();
    }
}
