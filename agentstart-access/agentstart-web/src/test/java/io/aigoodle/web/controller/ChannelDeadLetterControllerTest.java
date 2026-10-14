package io.aigoodle.web.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelEventLogService;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChannelDeadLetterControllerTest {
    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void administratorReplayUsesTrustedTenantAndWritesAggregateAudit() {
        ChannelEventLogService events = mock(ChannelEventLogService.class);
        ChannelAdministrationPolicy policy = mock(ChannelAdministrationPolicy.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        when(events.replayDeadLetters("trusted-tenant", List.of("event-1")))
                .thenReturn(new ChannelEventLogService.DeadLetterReplayResult(1, 1, 1));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("trusted-tenant")
                .userId("admin-1")
                .build());

        new ChannelDeadLetterController(events, policy, audits)
                .replay(new ChannelDeadLetterController.ReplayRequest(List.of("event-1")));

        verify(policy).requireAdministrator();
        verify(events).replayDeadLetters("trusted-tenant", List.of("event-1"));
        verify(audits)
                .success(
                        "CHANNEL_DEAD_LETTER_REPLAY",
                        "CHANNEL_EVENT_BATCH",
                        "dead-letters",
                        Map.of("requested", 1, "eligible", 1, "requeued", 1));
    }

    @Test
    void nonAdministratorCannotReadOrReplayDeadLetters() {
        ChannelEventLogService events = mock(ChannelEventLogService.class);
        ChannelAdministrationPolicy policy = mock(ChannelAdministrationPolicy.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        doThrow(new SecurityException("forbidden")).when(policy).requireAdministrator();
        ChannelDeadLetterController controller = new ChannelDeadLetterController(events, policy, audits);

        assertThatThrownBy(() -> controller.list(100)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controller.replay(new ChannelDeadLetterController.ReplayRequest(List.of("event-1"))))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(events, audits);
    }
}
