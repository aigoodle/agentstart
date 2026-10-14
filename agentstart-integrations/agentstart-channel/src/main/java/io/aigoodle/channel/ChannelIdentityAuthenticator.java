package io.aigoodle.channel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves and persists only identities explicitly verified by the host business system. */
public final class ChannelIdentityAuthenticator {
    public record Result(
            boolean allowed,
            boolean verified,
            String enterpriseUserId,
            String code,
            String message,
            String bindingUrl,
            Map<String, Object> metadata) {
        public Result {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    private static final String DEFAULT_MESSAGE = "当前渠道身份尚未绑定，请先完成员工身份验证后再使用机器人。";
    private final ChannelIdentityService identities;
    private final List<ChannelIdentityBindingProvider> providers;

    public ChannelIdentityAuthenticator(
            ChannelIdentityService identities, List<ChannelIdentityBindingProvider> providers) {
        this.identities = identities;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public Result authenticate(String tenantId, ChannelInboundEvent event) {
        ChannelIdentityService.Identity saved = identities.resolve(tenantId, event);
        if (trusted(saved)) return verified(saved.enterpriseUserId(), Map.of("source", "stored_binding"));

        ChannelIdentityBindingProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(event.provider(), event.channelId()))
                .findFirst()
                .orElse(null);
        if (provider == null) return notRequired(Map.of("source", "no_binding_provider"));

        ChannelIdentityBindingProvider.Resolution resolution =
                provider.resolve(new ChannelIdentityBindingProvider.Request(
                        tenantId,
                        event.provider(),
                        event.channelId(),
                        event.accountId(),
                        event.senderId(),
                        event.metadata()));
        if (resolution == null) return unbound(DEFAULT_MESSAGE, null, Map.of("source", "empty_provider_result"));
        if (resolution.status() == ChannelIdentityBindingProvider.Status.NOT_REQUIRED) {
            return notRequired(merge(resolution.metadata(), "source", "identity_not_required"));
        }
        if (resolution.status() == ChannelIdentityBindingProvider.Status.VERIFIED
                && present(resolution.enterpriseUserId())) {
            ChannelIdentityService.Identity persisted = identities.save(
                    tenantId,
                    event.provider(),
                    event.channelId(),
                    event.accountId(),
                    event.senderId(),
                    resolution.enterpriseUserId(),
                    "VERIFIED",
                    true);
            return verified(persisted.enterpriseUserId(), merge(resolution.metadata(), "source", "binding_provider"));
        }
        String code = resolution.status() == ChannelIdentityBindingProvider.Status.REJECTED
                ? "channel_identity_rejected"
                : "channel_identity_unbound";
        return new Result(
                false,
                false,
                null,
                code,
                present(resolution.message()) ? resolution.message() : DEFAULT_MESSAGE,
                resolution.bindingUrl(),
                resolution.metadata());
    }

    private static boolean trusted(ChannelIdentityService.Identity identity) {
        return identity != null
                && identity.enabled()
                && "VERIFIED".equalsIgnoreCase(identity.verificationStatus())
                && present(identity.enterpriseUserId());
    }

    private static Result verified(String employeeId, Map<String, Object> metadata) {
        return new Result(true, true, employeeId, "channel_identity_verified", null, null, metadata);
    }

    private static Result notRequired(Map<String, Object> metadata) {
        return new Result(true, false, null, "channel_identity_not_required", null, null, metadata);
    }

    private static Result unbound(String message, String bindingUrl, Map<String, Object> metadata) {
        return new Result(false, false, null, "channel_identity_unbound", message, bindingUrl, metadata);
    }

    private static Map<String, Object> merge(Map<String, Object> values, String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(values == null ? Map.of() : values);
        result.put(key, value);
        return result;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
