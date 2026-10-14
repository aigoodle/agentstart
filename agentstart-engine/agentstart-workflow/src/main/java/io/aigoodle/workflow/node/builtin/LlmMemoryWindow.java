package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import java.util.Locale;
import java.util.Map;

/** Normalizes current and legacy workflow-node memory settings. */
record LlmMemoryWindow(int size) {

    static final int DEFAULT_SIZE = 10;
    private static final int DISABLED = 0;

    static LlmMemoryWindow from(NodeDef node) {
        Object memoryConfiguration = node.get("memory");
        if (memoryConfiguration instanceof Map<?, ?> memory) {
            Object windowConfiguration = memory.get("window");
            if (windowConfiguration instanceof Map<?, ?> window) {
                boolean enabled = asBoolean(window.get("enabled"), true);
                int configuredSize = asInteger(window.get("size"), DEFAULT_SIZE);
                return new LlmMemoryWindow(enabled ? nonNegative(configuredSize) : DISABLED);
            }

            int legacySize = asInteger(memory.get("windows"), DISABLED);
            if (legacySize > 0) {
                return new LlmMemoryWindow(legacySize);
            }
        }

        Object memoryEnabled = node.get("memoryEnabled");
        if (memoryEnabled != null && !asBoolean(memoryEnabled, false)) {
            return new LlmMemoryWindow(DISABLED);
        }

        int configuredSize = node.getInt("memoryWindow", DISABLED);
        if (configuredSize > 0) {
            return new LlmMemoryWindow(configuredSize);
        }
        return new LlmMemoryWindow(asBoolean(memoryEnabled, false) ? DEFAULT_SIZE : DISABLED);
    }

    boolean enabled() {
        return size > 0;
    }

    private static int nonNegative(int value) {
        return Math.max(DISABLED, value);
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return defaultValue;
        }
        return switch (normalized) {
            case "true", "1", "yes", "on", "enable", "enabled" -> true;
            case "false", "0", "no", "off", "disable", "disabled" -> false;
            default -> defaultValue;
        };
    }

    private static int asInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException invalidNumber) {
            return defaultValue;
        }
    }
}
