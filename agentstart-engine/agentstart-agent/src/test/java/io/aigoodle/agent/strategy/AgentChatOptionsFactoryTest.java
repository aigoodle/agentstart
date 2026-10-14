package io.aigoodle.agent.strategy;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.agent.api.AgentDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Locks in the vendor-specific translation of the app-level
 * {@code model_settings_json} blob — the runtime piece that turns a normalized
 * {@code thinkingMode} into whichever body param the vendor actually expects.
 */
class AgentChatOptionsFactoryTest {

    @Test
    void returnsNullWhenNoSettings() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("openai")
                .modelName("gpt-4o")
                .build();
        assertNull(AgentChatOptionsFactory.build(def));
    }

    @Test
    void openAiCompatCommonParamsAreApplied() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("openai")
                .modelName("gpt-4o")
                .modelSettings(Map.of(
                        "temperature", 0.3,
                        "topP", 0.9,
                        "maxTokens", 512,
                        "presencePenalty", 0.1,
                        "frequencyPenalty", 0.2))
                .build();
        ChatOptions opts = AgentChatOptionsFactory.build(def);
        assertInstanceOf(OpenAiChatOptions.class, opts);
        OpenAiChatOptions o = (OpenAiChatOptions) opts;
        assertEquals(0.3, o.getTemperature());
        assertEquals(0.9, o.getTopP());
        assertEquals(512, o.getMaxTokens());
        assertEquals(0.1, o.getPresencePenalty());
        assertEquals(0.2, o.getFrequencyPenalty());
    }

    @Test
    void qwenDisabledThinkingMapsToEnableThinkingFalse() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("qwen")
                .modelName("qwen3-plus")
                .modelSettings(Map.of("thinkingMode", "disabled"))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        assertEquals(false, opts.getExtraBody().get("enable_thinking"));
    }

    @Test
    void qwenEnabledThinkingMapsToEnableThinkingTrue() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("qwen")
                .modelName("qwen3-plus")
                .modelSettings(Map.of("thinkingMode", "enabled"))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        assertEquals(true, opts.getExtraBody().get("enable_thinking"));
    }

    @Test
    void zhipuMapsThinkingToStructuredBodyField() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("zhipu")
                .modelName("glm-4-plus")
                .modelSettings(Map.of("thinkingMode", "disabled"))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        Object thinking = opts.getExtraBody().get("thinking");
        assertInstanceOf(Map.class, thinking);
        assertEquals("disabled", ((Map<?, ?>) thinking).get("type"));
    }

    @Test
    void volcengineMapsThinkingToStructuredBodyField() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("volcengine")
                .modelName("doubao-1-5-thinking-pro")
                .modelSettings(Map.of("thinkingMode", "disabled"))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        Object thinking = opts.getExtraBody().get("thinking");
        assertInstanceOf(Map.class, thinking);
        assertEquals("disabled", ((Map<?, ?>) thinking).get("type"));
    }

    @Test
    void openAiDisabledThinkingMapsToLowReasoningEffort() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("openai")
                .modelName("o3-mini")
                .modelSettings(Map.of("thinkingMode", "disabled"))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        assertEquals("low", opts.getReasoningEffort());
    }

    @Test
    void autoModeLeavesThinkingUntouched() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("qwen")
                .modelName("qwen3-plus")
                .modelSettings(Map.of("thinkingMode", "auto"))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        // 'auto' is the "no override" default — leave the model's own defaults intact.
        assertTrue(
                opts.getExtraBody() == null || !opts.getExtraBody().containsKey("enable_thinking"),
                "auto must not force a vendor-specific override");
    }

    @Test
    void ollamaThinkingModeUsesNativeToggle() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("ollama")
                .modelName("qwen3:8b")
                .modelSettings(Map.of("temperature", 0.5, "thinkingMode", "disabled"))
                .build();
        ChatOptions opts = AgentChatOptionsFactory.build(def);
        assertInstanceOf(OllamaChatOptions.class, opts);
        OllamaChatOptions o = (OllamaChatOptions) opts;
        assertEquals(0.5, o.getTemperature());
        assertNotNull(o.getThinkOption(), "disable/enable must populate the ThinkOption");
        assertSame(
                org.springframework.ai.ollama.api.ThinkOption.ThinkBoolean.DISABLED,
                o.getThinkOption(),
                "'disabled' must map to ThinkBoolean.DISABLED");
    }

    @Test
    void difyStyleSnakeCaseKeysAreAccepted() {
        // Mirror the actual payload the ModelPickerPopover ships:
        // enable_thinking is a boolean, keys are snake_case (max_tokens / top_p).
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("qwen")
                .modelName("qwen3.6-flash")
                .modelSettings(Map.of(
                        "temperature",
                        0.3,
                        "max_tokens",
                        8192,
                        "top_p",
                        0.80,
                        "top_k",
                        0,
                        "seed",
                        1234,
                        "repetition_penalty",
                        1.10,
                        "enable_web_search",
                        true,
                        "enable_thinking",
                        false,
                        "thinking_budget",
                        2048))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        assertEquals(0.3, opts.getTemperature());
        assertEquals(8192, opts.getMaxTokens());
        assertEquals(0.8, opts.getTopP());
        // enable_thinking:false ⇒ Qwen-body enable_thinking:false
        assertEquals(false, opts.getExtraBody().get("enable_thinking"));
        // Optional generic extras propagate too.
        assertEquals(2048, opts.getExtraBody().get("thinking_budget"));
        assertEquals(true, opts.getExtraBody().get("enable_search"));
        assertEquals(1234, opts.getExtraBody().get("seed"));
        assertEquals(1.10, opts.getExtraBody().get("repetition_penalty"));
    }

    @Test
    void unknownVendorPersistsRawExtraBody() {
        AgentDefinition def = AgentDefinition.builder()
                .modelProvider("custom-vendor")
                .modelName("some-model")
                .modelSettings(Map.of("temperature", 0.4, "extraBody", Map.of("custom_flag", 1)))
                .build();
        OpenAiChatOptions opts = (OpenAiChatOptions) AgentChatOptionsFactory.build(def);
        assertEquals(0.4, opts.getTemperature());
        assertEquals(1, opts.getExtraBody().get("custom_flag"));
    }
}
