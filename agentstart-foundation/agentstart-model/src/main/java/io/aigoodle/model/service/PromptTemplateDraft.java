package io.aigoodle.model.service;

import java.util.List;

/** Complete values required to create a prompt template. */
public record PromptTemplateDraft(
        String tenantId, String name, String category, String description, String content, List<String> tags) {}
