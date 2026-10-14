package io.aigoodle.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelIdentityService;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelIdentityControllerTest {
    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void administratorSaveUsesTrustedTenantAndWritesActorCorrelatableAudit() {
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelAdministrationPolicy policy = mock(ChannelAdministrationPolicy.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        ChannelIdentityService.Identity saved = new ChannelIdentityService.Identity(
                "identity-1",
                "trusted-tenant",
                "openclaw",
                "qqbot",
                "account-1",
                "external-1",
                "employee-7",
                "VERIFIED",
                true);
        when(identities.save(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean()))
                .thenReturn(saved);
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("admin-1")
                .build());
        ChannelIdentityController controller = new ChannelIdentityController(identities, policy, audits);

        controller.save(new ChannelIdentityController.SaveRequest(
                "openclaw", "qqbot", "account-1", "external-1", "employee-7", "VERIFIED", true));

        verify(policy).requireAdministrator();
        verify(identities)
                .save("trusted-tenant", "openclaw", "qqbot", "account-1", "external-1", "employee-7", "VERIFIED", true);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> details = ArgumentCaptor.forClass(Map.class);
        verify(audits)
                .success(eq("CHANNEL_IDENTITY_SAVE"), eq("CHANNEL_IDENTITY"), eq("identity-1"), details.capture());
        assertThat(details.getValue())
                .containsEntry("enterpriseUserId", "employee-7")
                .containsEntry("accountId", "account-1")
                .doesNotContainKeys("credentials", "secret", "token");
    }

    @Test
    void nonAdministratorCannotMutateIdentityOrCreateAuditSuccess() {
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelAdministrationPolicy policy = mock(ChannelAdministrationPolicy.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        doThrow(new SecurityException("forbidden")).when(policy).requireAdministrator();
        ChannelIdentityController controller = new ChannelIdentityController(identities, policy, audits);

        assertThatThrownBy(() -> controller.delete("identity-1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("forbidden");

        verifyNoInteractions(identities, audits);
    }
}
