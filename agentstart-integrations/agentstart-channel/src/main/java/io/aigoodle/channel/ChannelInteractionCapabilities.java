package io.aigoodle.channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stable connector-hub vocabulary used by workflow nodes instead of channel-name switches. */
public final class ChannelInteractionCapabilities {
    public static final String KEY = "humanInteraction";

    private ChannelInteractionCapabilities() {}

    public static Map<String, Object> normalize(String channelId, Map<String, Object> original) {
        Map<String, Object> result = new LinkedHashMap<>(original == null ? Map.of() : original);
        if (result.get(KEY) instanceof Map<?, ?>) return result;
        String id = channelId == null ? "" : channelId.toLowerCase(Locale.ROOT);
        boolean sms = id.contains("sms") || id.contains("ntfy");
        boolean email = id.contains("email") || id.contains("mail");
        boolean voice = id.contains("whatsapp")
                || id.contains("telegram")
                || id.contains("weixin")
                || id.contains("wecom")
                || id.contains("feishu")
                || id.contains("line")
                || id.contains("signal");
        List<String> content = new ArrayList<>(List.of("TEXT", "LINK"));
        if (!sms) content.add("IMAGE");
        if (!sms && !email) content.add("FILE");
        if (voice) content.add("AUDIO");
        Map<String, Object> human = new LinkedHashMap<>();
        human.put("contentTypes", content);
        human.put("outbound", bool(result.get("agentStartOutbound")));
        human.put("inboundReply", bool(result.get("agentStartInbound")));
        human.put("nativeForm", false);
        human.put("buttons", !sms && !email);
        human.put("html", email);
        human.put("publicWebLink", true);
        human.put("recommendedPresentation", email ? "WEB_FORM" : sms ? "TEXT" : "AUTO");
        human.put("fallbackOrder", List.of("CHANNEL_NATIVE", "WEB_FORM", "TEXT"));
        result.put(KEY, human);
        return result;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b && b;
    }
}
