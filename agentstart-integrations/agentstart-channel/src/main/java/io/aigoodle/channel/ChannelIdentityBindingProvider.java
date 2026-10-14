package io.aigoodle.channel;

import java.util.Map;

/**
 * Host-system boundary for resolving a channel-native user to a trusted enterprise employee.
 *
 * <p>The connector deliberately does not query or own an employee directory. A host may implement
 * this SPI using an existing channel binding, SSO identity, administrator approval, or a verified
 * mobile-number flow. Implementations must never return {@link Status#VERIFIED} for an unverified
 * name or phone-number match.
 */
public interface ChannelIdentityBindingProvider {
    enum Status {
        NOT_REQUIRED,
        VERIFIED,
        UNBOUND,
        REJECTED
    }

    record Request(
            String tenantId,
            String provider,
            String channelId,
            String accountId,
            String externalUserId,
            Map<String, Object> attributes) {
        public Request {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    record Resolution(
            Status status, String enterpriseUserId, String message, String bindingUrl, Map<String, Object> metadata) {
        public Resolution {
            status = status == null ? Status.UNBOUND : status;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        public static Resolution verified(String enterpriseUserId) {
            return new Resolution(Status.VERIFIED, enterpriseUserId, null, null, Map.of());
        }

        /** Identity enforcement is disabled for this tenant/channel, so message handling may continue. */
        public static Resolution notRequired() {
            return new Resolution(Status.NOT_REQUIRED, null, null, null, Map.of());
        }

        public static Resolution unbound(String message, String bindingUrl) {
            return new Resolution(Status.UNBOUND, null, message, bindingUrl, Map.of());
        }
    }

    /** Whether this provider owns identity resolution for the channel. */
    boolean supports(String provider, String channelId);

    /** Resolve an identity or start/describe the host-owned binding flow. */
    Resolution resolve(Request request);
}
