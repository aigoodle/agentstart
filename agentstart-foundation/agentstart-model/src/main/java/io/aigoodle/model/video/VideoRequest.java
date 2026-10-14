package io.aigoodle.model.video;

import java.util.Map;

public record VideoRequest(String prompt, Map<String, Object> parameters) {
    public VideoRequest {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("Video prompt is required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
