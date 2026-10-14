package io.aigoodle.channel.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Account ownership and runtime identity semantics published to channel clients. */
public record ChannelAccountModel(
        Scope scope,
        InstancePolicy instancePolicy,
        boolean ownerRequired,
        String ownerLabel,
        String ownerPlaceholder,
        IdentityBridge identityBridge) {

    public enum Scope {
        PERSONAL,
        TENANT
    }

    public enum InstancePolicy {
        SINGLE,
        MULTIPLE
    }

    public record IdentityBridge(
            boolean enabled,
            String mode,
            String externalIdentityLabel,
            String enterpriseIdentityLabel,
            String description) {
        public Map<String, Object> toMetadata() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("enabled", enabled);
            put(result, "mode", mode);
            put(result, "externalIdentityLabel", externalIdentityLabel);
            put(result, "enterpriseIdentityLabel", enterpriseIdentityLabel);
            put(result, "description", description);
            return Map.copyOf(result);
        }
    }

    public ChannelAccountModel {
        scope = scope == null ? Scope.PERSONAL : scope;
        instancePolicy = instancePolicy == null ? InstancePolicy.MULTIPLE : instancePolicy;
    }

    public static ChannelAccountModel personal() {
        return new ChannelAccountModel(
                Scope.PERSONAL, InstancePolicy.MULTIPLE, true, "员工 / 所有者 ID", "例如 employee-001", null);
    }

    public static ChannelAccountModel tenant(IdentityBridge identityBridge) {
        return new ChannelAccountModel(Scope.TENANT, InstancePolicy.MULTIPLE, false, null, null, identityBridge);
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope.name());
        result.put("instancePolicy", instancePolicy.name());
        result.put("ownerRequired", ownerRequired);
        put(result, "ownerLabel", ownerLabel);
        put(result, "ownerPlaceholder", ownerPlaceholder);
        if (identityBridge != null) result.put("identityBridge", identityBridge.toMetadata());
        return Map.copyOf(result);
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
