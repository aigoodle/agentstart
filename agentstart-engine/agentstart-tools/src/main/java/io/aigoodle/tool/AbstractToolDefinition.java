package io.aigoodle.tool;

import java.util.Map;

/**
 * Convenience base with typed argument helpers.
 */
public abstract class AbstractToolDefinition implements ToolDefinition {

    protected String stringArgument(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        return value == null ? null : String.valueOf(value);
    }

    protected String stringArgument(Map<String, Object> arguments, String name, String defaultValue) {
        String value = stringArgument(arguments, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    protected double doubleArgument(Map<String, Object> arguments, String name, double defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /** @deprecated since 0.1.0; use {@link #stringArgument(Map, String)}. */
    @Deprecated
    protected String str(Map<String, Object> arguments, String name) {
        return stringArgument(arguments, name);
    }

    /** @deprecated since 0.1.0; use {@link #stringArgument(Map, String, String)}. */
    @Deprecated
    protected String str(Map<String, Object> arguments, String name, String defaultValue) {
        return stringArgument(arguments, name, defaultValue);
    }

    /** @deprecated since 0.1.0; use {@link #doubleArgument(Map, String, double)}. */
    @Deprecated
    protected double dbl(Map<String, Object> arguments, String name, double defaultValue) {
        return doubleArgument(arguments, name, defaultValue);
    }
}
