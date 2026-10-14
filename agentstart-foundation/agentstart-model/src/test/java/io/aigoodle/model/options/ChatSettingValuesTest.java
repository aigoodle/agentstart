package io.aigoodle.model.options;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatSettingValuesTest {

    @Test
    void readsCanonicalNameBeforeLegacyAlias() {
        ChatSettingValues settings = new ChatSettingValues(Map.of(
                "topP", "0.8",
                "top_p", "0.4"));

        assertThat(settings.decimal("topP", "top_p")).isEqualTo(0.8);
    }

    @Test
    void tolerantlyConvertsPersistedScalarAndListValues() {
        ChatSettingValues settings = new ChatSettingValues(Map.of(
                "max_tokens", "2048",
                "enable_search", "yes",
                "stop", "END, STOP"));

        assertThat(settings.integer("maxTokens", "max_tokens")).isEqualTo(2048);
        assertThat(settings.bool("enable_search")).isTrue();
        assertThat(settings.stringList("stop")).containsExactly("END", "STOP");
    }

    @Test
    void normalizesThinkingModeAndLeavesAutoUnspecified() {
        assertThat(new ChatSettingValues(Map.of("enable_thinking", true)).thinkingMode())
                .isEqualTo("enabled");
        assertThat(new ChatSettingValues(Map.of("thinkingMode", "off")).thinkingMode())
                .isEqualTo("disabled");
        assertThat(new ChatSettingValues(Map.of("thinkingMode", "auto")).thinkingMode())
                .isNull();
    }
}
