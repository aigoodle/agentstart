package io.aigoodle.channel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelIdentityAuthenticatorTest {
    private final ChannelInboundEvent event = new ChannelInboundEvent(
            "native",
            "wecom",
            "bot-1",
            "message-1",
            "wecom-user-1",
            "wecom-user-1",
            "hello",
            "TEXT",
            List.of(),
            Map.of(),
            Instant.now(),
            false,
            Map.of("transport", "websocket"));

    @Test
    void persistsOnlyProviderVerifiedEmployeeIdentity() {
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        when(identities.save(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("VERIFIED"),
                        eq(true)))
                .thenReturn(new ChannelIdentityService.Identity(
                        "i1", "tenant-1", "native", "wecom", "bot-1", "wecom-user-1", "employee-42", "VERIFIED", true));
        ChannelIdentityBindingProvider provider = new ChannelIdentityBindingProvider() {
            @Override
            public boolean supports(String provider, String channelId) {
                return "native".equals(provider) && "wecom".equals(channelId);
            }

            @Override
            public Resolution resolve(Request request) {
                assertEquals("wecom-user-1", request.externalUserId());
                return Resolution.verified("employee-42");
            }
        };

        var result = new ChannelIdentityAuthenticator(identities, List.of(provider)).authenticate("tenant-1", event);

        assertTrue(result.verified());
        assertEquals("employee-42", result.enterpriseUserId());
        verify(identities)
                .save("tenant-1", "native", "wecom", "bot-1", "wecom-user-1", "employee-42", "VERIFIED", true);
    }

    @Test
    void allowsMessageWhenChannelHasNoIdentityBindingProvider() {
        ChannelIdentityService identities = mock(ChannelIdentityService.class);

        var result = new ChannelIdentityAuthenticator(identities, List.of()).authenticate("tenant-1", event);

        assertTrue(result.allowed());
        assertFalse(result.verified());
        assertEquals("channel_identity_not_required", result.code());
        verify(identities, never())
                .save(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean());
    }

    @Test
    void allowsMessageWhenProviderSaysIdentityIsNotRequired() {
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelIdentityBindingProvider provider = new ChannelIdentityBindingProvider() {
            @Override
            public boolean supports(String provider, String channelId) {
                return true;
            }

            @Override
            public Resolution resolve(Request request) {
                return Resolution.notRequired();
            }
        };

        var result = new ChannelIdentityAuthenticator(identities, List.of(provider)).authenticate("tenant-1", event);

        assertTrue(result.allowed());
        assertFalse(result.verified());
        assertEquals("channel_identity_not_required", result.code());
    }
}
