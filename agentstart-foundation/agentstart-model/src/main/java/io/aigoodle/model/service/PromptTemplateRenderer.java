package io.aigoodle.model.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and renders the shared {@code {{#variable#}}} prompt syntax. */
final class PromptTemplateRenderer {

    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("\\{\\{#\\s*([a-zA-Z0-9_\\-.]+)\\s*#}}");

    List<String> referencedVariables(String template) {
        if (template == null) {
            return List.of();
        }
        List<String> variables = new ArrayList<>();
        Matcher references = VARIABLE_REFERENCE.matcher(template);
        while (references.find()) {
            String variableName = references.group(1);
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
        }
        return variables;
    }

    String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher references = VARIABLE_REFERENCE.matcher(template);
        StringBuilder renderedTemplate = new StringBuilder();
        while (references.find()) {
            String variableName = references.group(1);
            Object value = variables == null ? null : variables.get(variableName);
            references.appendReplacement(
                    renderedTemplate, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        references.appendTail(renderedTemplate);
        return renderedTemplate.toString();
    }
}
