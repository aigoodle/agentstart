package io.aigoodle.model.options;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides tolerant, alias-aware typed access to persisted chat settings. */
final class ChatSettingValues {

    private final Map<String, Object> settings;

    ChatSettingValues(Map<String, Object> settings) {
        this.settings = settings;
    }

    Object first(String... names) {
        for (String name : names) {
            Object value = settings.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    Double decimal(String... names) {
        Object value = first(names);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    Integer integer(String... names) {
        Object value = first(names);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    Boolean bool(String... names) {
        Object value = first(names);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return switch (String.valueOf(value).trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> Boolean.TRUE;
            case "false", "0", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    List<String> stringList(String... names) {
        Object value = first(names);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (String item : text.split(",")) {
            String trimmedItem = item.trim();
            if (!trimmedItem.isEmpty()) {
                values.add(trimmedItem);
            }
        }
        return values;
    }

    String thinkingMode() {
        Object value = first("thinkingMode", "enable_thinking", "thinking_mode");
        if (value instanceof Boolean bool) {
            return bool ? "enabled" : "disabled";
        }
        if (value == null) {
            return null;
        }
        String mode = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "true", "on", "enable", "enabled" -> "enabled";
            case "false", "off", "disable", "disabled" -> "disabled";
            default -> null;
        };
    }
}
