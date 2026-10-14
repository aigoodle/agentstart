package io.aigoodle.workflow.variable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders templates containing {@code {{#namespace.field#}}} references against a
 * {@link VariablePool}, the same placeholder syntax Dify uses. Missing references
 * resolve to an empty string.
 */
public final class VariableResolver {

    private static final Pattern REF = Pattern.compile("\\{\\{#\\s*([a-zA-Z0-9_\\-.]+)\\s*#}}");

    private VariableResolver() {}

    public static String render(String template, VariablePool pool) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher m = REF.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1);
            Object value = pool.get(path);
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** True if the template references at least one variable. */
    public static boolean hasReferences(String template) {
        return template != null && REF.matcher(template).find();
    }
}
