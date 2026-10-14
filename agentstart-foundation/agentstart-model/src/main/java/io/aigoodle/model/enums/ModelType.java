package io.aigoodle.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

/**
 * The capability category of a model, mirroring Dify's model_runtime taxonomy.
 */
public enum ModelType {
    LLM("llm"),
    VIDEO("video"),
    TEXT_EMBEDDING("text-embedding"),
    RERANK("rerank"),
    SPEECH2TEXT("speech2text"),
    TTS("tts"),
    MODERATION("moderation");

    @EnumValue
    private final String value;

    ModelType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ModelType of(String value) {
        return Arrays.stream(values())
                .filter(t -> t.value.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown model type: " + value));
    }
}
