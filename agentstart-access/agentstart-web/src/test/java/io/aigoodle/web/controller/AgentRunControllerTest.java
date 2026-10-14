package io.aigoodle.web.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.*;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentRunControllerTest {
    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void batchResumeRequiresAdministrationAndWritesSecretFreeAggregateAudit() {
        AgentRuntimeRegistry runtime = mock(AgentRuntimeRegistry.class);
        ChannelAdministrationPolicy administration = mock(ChannelAdministrationPolicy.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        AgentResponse completed =
                AgentResponse.forConversation("conversation-1").complete("done");
        when(runtime.resumeForTenant(eq("tenant-a"), eq("run-1"), any())).thenReturn(completed);
        UserContextHolder.set(
                CurrentUser.builder().tenantId("tenant-a").userId("admin-1").build());
        AgentRunController controller = new AgentRunController(runtime, administration, audits);

        controller.resume(
                "run-1",
                new AgentResumeCommand(
                        null,
                        null,
                        Map.of(
                                "approval-1", AgentResumeCommand.Decision.APPROVE,
                                "approval-2", AgentResumeCommand.Decision.DENY)));

        verify(administration).requireAdministrator();
        verify(audits)
                .success(
                        "AGENT_RUN_RESUME",
                        "AGENT_RUN",
                        "run-1",
                        Map.of(
                                "decisionCount",
                                2,
                                "approvedCount",
                                1L,
                                "rejectedCount",
                                1L,
                                "resultStatus",
                                "COMPLETED"));
    }

    @Test
    void authorizationFailureStopsResumeBeforeRuntimeMutation() {
        AgentRuntimeRegistry runtime = mock(AgentRuntimeRegistry.class);
        ChannelAdministrationPolicy administration = mock(ChannelAdministrationPolicy.class);
        ChannelAuditService audits = mock(ChannelAuditService.class);
        doThrow(new SecurityException("forbidden")).when(administration).requireAdministrator();
        UserContextHolder.set(CurrentUser.builder().tenantId("tenant-a").build());
        AgentRunController controller = new AgentRunController(runtime, administration, audits);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.resume(
                        "run-1", new AgentResumeCommand("approval-1", AgentResumeCommand.Decision.APPROVE)))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(runtime, audits);
    }
}
