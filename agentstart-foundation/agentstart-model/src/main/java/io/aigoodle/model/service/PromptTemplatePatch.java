package io.aigoodle.model.service;

import java.util.List;

/** Nullable changes applied to an existing prompt template. */
public record PromptTemplatePatch(
        String name, String category, String description, String content, List<String> tags) {}
