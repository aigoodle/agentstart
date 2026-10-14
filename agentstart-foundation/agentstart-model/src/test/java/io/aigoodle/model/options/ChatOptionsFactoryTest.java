package io.aigoodle.model.options;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

class ChatOptionsFactoryTest {

    @Test
    void preservesQwenModelNameWhenBuildingPerRequestOptions() {
        OpenAiChatOptions options = (OpenAiChatOptions)
                ChatOptionsFactory.buildFromSettings("qwen", "qwen3.7-plus", Map.of("enable_thinking", false));

        assertThat(options.getModel()).isEqualTo("qwen3.7-plus");
        assertThat(options.getExtraBody()).containsEntry("enable_thinking", false);
    }

    @Test
    void preservesOllamaModelNameWhenBuildingPerRequestOptions() {
        OllamaChatOptions options = (OllamaChatOptions)
                ChatOptionsFactory.buildFromSettings("ollama", "qwen3:8b", Map.of("temperature", 0.4));

        assertThat(options.getModel()).isEqualTo("qwen3:8b");
        assertThat(options.getTemperature()).isEqualTo(0.4);
    }
}
