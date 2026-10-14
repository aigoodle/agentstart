package io.aigoodle.workflow.interaction;

import java.util.Map;

/** Chooses a usable presentation from connector-declared capabilities with deterministic fallback. */
public final class HumanInteractionPresentationPlanner {
    private HumanInteractionPresentationPlanner() {}

    public static String choose(String requested, Map<String, Object> capabilities) {
        String mode = requested == null ? "AUTO" : requested.toUpperCase();
        @SuppressWarnings("unchecked")
        Map<String, Object> human =
                capabilities != null && capabilities.get("humanInteraction") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
        if ("CHAT_COMPONENT".equals(mode)) return "CHAT_COMPONENT";
        if ("WEB_FORM".equals(mode)) return "WEB_FORM";
        if ("TEXT".equals(mode)) return "TEXT";
        if ("CHANNEL_NATIVE".equals(mode) && Boolean.TRUE.equals(human.get("nativeForm"))) return "CHANNEL_NATIVE";
        if (Boolean.TRUE.equals(human.get("nativeForm"))) return "CHANNEL_NATIVE";
        if (Boolean.TRUE.equals(human.get("publicWebLink"))) return "WEB_FORM";
        return "TEXT";
    }
}
