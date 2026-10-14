package io.aigoodle.agent.entity;

import java.util.Locale;

/** Supported application execution modes persisted by {@link AppEntity}. */
public enum AppMode {
    AGENT,
    CHAT,
    WORKFLOW,
    CHATFLOW,
    COMPLETION;

    public static AppMode from(String value) {
        if (value == null || value.isBlank()) {
            return AGENT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("mode must be agent, chat, workflow, chatflow or completion", exception);
        }
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isFlow() {
        return this == WORKFLOW || this == CHATFLOW;
    }

    public boolean isAgent() {
        return this == AGENT;
    }
}
