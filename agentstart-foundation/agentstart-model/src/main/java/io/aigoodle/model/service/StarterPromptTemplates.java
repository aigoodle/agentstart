package io.aigoodle.model.service;

import java.util.List;

/** Curated templates installed for an empty tenant prompt library. */
final class StarterPromptTemplates {

    private StarterPromptTemplates() {}

    static List<PromptTemplateDraft> forTenant(String tenantId) {
        return List.of(
                new PromptTemplateDraft(
                        tenantId,
                        "summarize-en",
                        "summarization",
                        "Concise English summary in one paragraph.",
                        "You are a concise summarizer. Read the passage and write ONE paragraph "
                                + "capturing the essential points.\n\nPassage:\n{{#input#}}",
                        List.of("summary", "en")),
                new PromptTemplateDraft(
                        tenantId,
                        "classify-intent",
                        "classifier",
                        "Route a user message into an intent id (uses {{#categories#}}).",
                        "You are a strict intent classifier. Pick ONE category id from the list.\n"
                                + "Reply with ONLY the id.\nCategories:\n{{#categories#}}",
                        List.of("classifier")),
                new PromptTemplateDraft(
                        tenantId,
                        "extract-json",
                        "extraction",
                        "Extract structured JSON matching a supplied schema.",
                        "Extract the fields defined by the JSON schema from the input. Reply with "
                                + "ONLY the JSON object, no code fences, no prose. Use null when a field "
                                + "is missing.\n\nSchema:\n{{#schema#}}\n\nInput:\n{{#input#}}",
                        List.of("json", "extraction")));
    }
}
